package com.sprint.bank.account.dto;

import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.account.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private Long id;
    private String accountNumber;
    private AccountType type;
    private BigDecimal balance;
    private AccountStatus status;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private LocalDateTime createdAt;
}
