package com.sprint.bank.admin.dto;

import com.sprint.bank.account.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AccountStatusRequest(
        @NotNull(message = "Account status is required (ACTIVE, FROZEN, or CLOSED)")
        AccountStatus status
) {
}
