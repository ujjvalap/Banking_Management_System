package com.sprint.bank.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[+()0-9 .-]{7,20}$", message = "Enter a valid phone number")
        String phone,

        @NotBlank(message = "Address is required")
        @Size(max = 300, message = "Address cannot exceed 300 characters")
        String address
) {
}
