package com.sprint.bank.auth.service;

import com.sprint.bank.auth.entity.LoginAttempt;
import com.sprint.bank.auth.repository.LoginAttemptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class LoginAttemptService {

    private final LoginAttemptRepository repository;
    private final int maxAttempts;
    private final long lockDurationMinutes;

    public LoginAttemptService(
            LoginAttemptRepository repository,
            @Value("${app.security.max-login-attempts:5}") int maxAttempts,
            @Value("${app.security.lock-duration-minutes:15}") long lockDurationMinutes) {
        this.repository = repository;
        this.maxAttempts = maxAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String email, boolean successful) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        LoginAttempt attempt = repository.findWithLockByEmail(normalized)
                .orElseGet(() -> createSafely(normalized));

        if (successful) {
            attempt.reset();
        } else {
            attempt.registerFailure(maxAttempts, lockDurationMinutes, now);
        }
        repository.save(attempt);
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        return repository.findByEmail(normalized)
                .map(attempt -> attempt.isCurrentlyLocked(now))
                .orElse(false);
    }

    private LoginAttempt createSafely(String normalizedEmail) {
        try {
            return repository.saveAndFlush(new LoginAttempt(normalizedEmail));
        } catch (DataIntegrityViolationException ex) {
            return repository.findWithLockByEmail(normalizedEmail)
                    .orElseThrow(() -> ex);
        }
    }
}
