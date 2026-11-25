package com.sprint.bank.auth.service;

import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.auth.exception.InvalidCredentialsException;
import com.sprint.bank.common.constants.AppConstants;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;
    private final AuditService auditService;

    public PasswordService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenRevocationService tokenRevocationService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRevocationService = tokenRevocationService;
        this.auditService = auditService;
    }

    @Transactional
    public void change(Long userId, String currentPassword, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > AppConstants.PASSWORD_MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        tokenRevocationService.revokeAll(userId);
        auditService.log(user.getEmail(), "PASSWORD_CHANGED", "User",
                String.valueOf(userId), "Password updated and active sessions revoked");
    }
}
