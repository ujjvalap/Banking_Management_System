package com.sprint.bank.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Verification token is required")
        @Size(max = 128)
        String token
) {
}
