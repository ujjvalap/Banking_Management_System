package com.sprint.bank.customer.dto;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String phone,
        String address,
        String pan,
        boolean enabled,
        int accountCount,
        Instant createdAt
) {
}
