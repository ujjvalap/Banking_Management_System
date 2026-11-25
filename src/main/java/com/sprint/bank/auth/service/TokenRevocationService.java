package com.sprint.bank.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenRevocationService {

    private final RefreshTokenService refreshTokenService;

    public TokenRevocationService(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenService.revokeAll(userId);
    }
}
