package com.sprint.bank.auth.service;

import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public LogoutService(
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            AuditService auditService) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAll(userId);
        userRepository.findById(userId).ifPresent(user ->
                auditService.log(user.getEmail(), "USER_LOGOUT", "User",
                        String.valueOf(userId), "Revoked access and refresh sessions"));
    }

    @Transactional
    public void logoutByRefreshToken(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }
}
