package com.sprint.bank.account.dto;

import com.sprint.bank.account.enums.AccountType;
import jakarta.validation.constraints.NotNull;

public record AccountRequest(
        @NotNull(message = "Account type is required (SAVINGS or CURRENT)")
        AccountType type
) {
}
