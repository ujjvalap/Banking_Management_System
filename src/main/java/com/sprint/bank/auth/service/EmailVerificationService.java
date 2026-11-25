package com.sprint.bank.auth.service;

import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.auth.entity.EmailVerificationToken;
import com.sprint.bank.auth.exception.InvalidTokenException;
import com.sprint.bank.auth.repository.EmailVerificationTokenRepository;
import com.sprint.bank.common.util.TokenCryptoUtil;
import com.sprint.bank.exception.EmailDeliveryUnavailableException;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final AuditService auditService;
    private final long expirationMillis;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailDeliveryService emailDeliveryService,
            AuditService auditService,
            @Value("${app.security.email-verification-expiration:86400000}") long expirationMillis) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailDeliveryService = emailDeliveryService;
        this.auditService = auditService;
        this.expirationMillis = expirationMillis;
    }

    @Transactional
    public void resendVerification(String email) {
        if (!emailDeliveryService.isAvailable()) {
            throw new EmailDeliveryUnavailableException();
        }
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(user -> {
            if (!user.isEnabled()) {
                sendVerification(user);
            }
        });
    }

    @Transactional
    public void sendVerification(User user) {
        if (user.isEnabled()) {
            return;
        }
        String rawToken = TokenCryptoUtil.generateRandomToken(32);
        tokenRepository.deleteAllByUser_IdAndUsedFalse(user.getId());
        tokenRepository.save(new EmailVerificationToken(
                user,
                TokenCryptoUtil.sha256Hex(rawToken),
                Instant.now().plusMillis(expirationMillis)
        ));
        emailDeliveryService.sendVerification(user.getEmail(), rawToken);
    }

    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException();
        }
        EmailVerificationToken stored = tokenRepository.findByTokenHash(TokenCryptoUtil.sha256Hex(rawToken))
                .orElseThrow(InvalidTokenException::new);
        if (stored.isUsed() || !stored.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidTokenException();
        }
        stored.getUser().markEmailVerified();
        stored.markUsed();
        auditService.log(stored.getUser().getEmail(), "EMAIL_VERIFIED", "User",
                String.valueOf(stored.getUser().getId()), "Customer verified email address");
    }
}
