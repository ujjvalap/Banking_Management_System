package com.sprint.bank.transaction.dto;

import com.sprint.bank.transaction.enums.TransactionType;
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
public class TransactionResponse {

    private Long id;
    private Long accountId;
    private String accountNumber;
    private TransactionType type;
    private BigDecimal amount;
    private String description;
    private String referenceId;
    private LocalDateTime timestamp;
}
