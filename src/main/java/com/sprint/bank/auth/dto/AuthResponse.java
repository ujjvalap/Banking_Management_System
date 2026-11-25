package com.sprint.bank.auth.dto;

import com.sprint.bank.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user,
        String message
) {
    public AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {
        this(accessToken, refreshToken, tokenType, expiresIn, user, null);
    }

    public static AuthResponse verificationRequired(UserResponse user) {
        return new AuthResponse(null, null, null, 0, user, "Registration successful. Check your email to verify your account.");
    }
}
