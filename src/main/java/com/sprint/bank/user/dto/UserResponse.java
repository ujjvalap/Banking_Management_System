package com.sprint.bank.user.dto;

import com.sprint.bank.user.enums.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String address,
        String pan,
        Role role,
        boolean enabled,
        Instant createdAt
) {
}
