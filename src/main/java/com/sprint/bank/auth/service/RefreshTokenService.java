package com.sprint.bank.auth.service;

import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.auth.dto.TokenResponse;
import com.sprint.bank.auth.entity.RefreshToken;
import com.sprint.bank.auth.exception.InvalidTokenException;
import com.sprint.bank.auth.repository.RefreshTokenRepository;
import com.sprint.bank.common.constants.AppConstants;
import com.sprint.bank.common.util.TokenCryptoUtil;
import com.sprint.bank.security.jwt.JwtService;
import com.sprint.bank.security.principal.CustomUserDetails;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final long refreshExpiration;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            UserRepository userRepository,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            AuditService auditService,
            @Value("${app.jwt.refresh-expiration:604800000}") long refreshExpiration) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.refreshExpiration = refreshExpiration;
    }

    @Transactional
    public String create(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidTokenException::new);

        String rawToken = TokenCryptoUtil.generateRandomToken(64);
        repository.save(new RefreshToken(
                user,
                TokenCryptoUtil.sha256Hex(rawToken),
                Instant.now().plusMillis(refreshExpiration),
                UUID.randomUUID().toString()
        ));
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException();
        }
        String tokenHash = TokenCryptoUtil.sha256Hex(rawToken.trim());

        RefreshToken stored = repository.findWithLockByTokenHash(tokenHash)
                .orElseThrow(InvalidTokenException::new);

        if (stored.isRevoked() || !stored.getExpiresAt().isAfter(Instant.now())) {
            repository.findAllByFamilyId(stored.getFamilyId())
                    .forEach(RefreshToken::revoke);
            auditService.log(stored.getUser().getEmail(), "REFRESH_TOKEN_REUSE_DETECTED", "RefreshToken",
                    stored.getFamilyId(), "Revoked token family due to reuse or expiry");
            throw new InvalidTokenException();
        }

        stored.revoke();

        User user = stored.getUser();
        if (!user.isEnabled() || loginAttemptService.isLocked(user.getEmail())) {
            throw new InvalidTokenException();
        }

        CustomUserDetails principal = new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(AppConstants.ROLE_PREFIX + user.getRole().name())),
                user.isEnabled(),
                user.getTokenVersion(),
                true
        );

        String newAccessToken = jwtService.generateAccessToken(principal);
        String newRefreshToken = TokenCryptoUtil.generateRandomToken(64);

        repository.save(new RefreshToken(
                user,
                TokenCryptoUtil.sha256Hex(newRefreshToken),
                Instant.now().plusMillis(refreshExpiration),
                stored.getFamilyId()
        ));

        return new TokenResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtService.getAccessExpiration() / 1000
        );
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findWithLockByTokenHash(TokenCryptoUtil.sha256Hex(rawToken.trim()))
                .ifPresent(token -> revokeAll(token.getUser().getId()));
    }

    @Transactional
    public void revokeAll(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidTokenException::new);
        user.revokeSessions();
        repository.findAllByUser_IdAndRevokedFalse(userId)
                .forEach(RefreshToken::revoke);
    }
}
