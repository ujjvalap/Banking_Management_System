package com.sprint.bank.auth.service;

import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.auth.entity.PasswordResetToken;
import com.sprint.bank.auth.exception.InvalidTokenException;
import com.sprint.bank.auth.repository.PasswordResetTokenRepository;
import com.sprint.bank.common.constants.AppConstants;
import com.sprint.bank.common.util.TokenCryptoUtil;
import com.sprint.bank.exception.EmailDeliveryUnavailableException;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailDeliveryService emailDeliveryService;
    private final TokenRevocationService tokenRevocationService;
    private final AuditService auditService;
    private final long expirationMillis;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailDeliveryService emailDeliveryService,
            TokenRevocationService tokenRevocationService,
            AuditService auditService,
            @Value("${app.security.password-reset-expiration:900000}") long expirationMillis) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailDeliveryService = emailDeliveryService;
        this.tokenRevocationService = tokenRevocationService;
        this.auditService = auditService;
        this.expirationMillis = expirationMillis;
    }

    @Transactional
    public void requestReset(String email) {
        if (!emailDeliveryService.isAvailable()) {
            throw new EmailDeliveryUnavailableException();
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(normalized).ifPresent(user -> {
            String rawToken = TokenCryptoUtil.generateRandomToken(32);
            tokenRepository.deleteAllByUser_IdAndUsedFalse(user.getId());
            tokenRepository.save(new PasswordResetToken(
                    user,
                    TokenCryptoUtil.sha256Hex(rawToken),
                    Instant.now().plusMillis(expirationMillis)
            ));
            emailDeliveryService.sendPasswordReset(user.getEmail(), rawToken);
            auditService.log(user.getEmail(), "PASSWORD_RESET_REQUESTED", "User",
                    String.valueOf(user.getId()), "Password reset link issued");
        });
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > AppConstants.PASSWORD_MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
        }
        PasswordResetToken stored = tokenRepository.findByTokenHash(TokenCryptoUtil.sha256Hex(rawToken))
                .orElseThrow(InvalidTokenException::new);
        if (stored.isUsed() || !stored.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidTokenException();
        }
        stored.getUser().changePassword(passwordEncoder.encode(newPassword));
        stored.markUsed();
        tokenRevocationService.revokeAll(stored.getUser().getId());
        auditService.log(stored.getUser().getEmail(), "PASSWORD_RESET_COMPLETED", "User",
                String.valueOf(stored.getUser().getId()), "Password reset and active sessions revoked");
    }
}
