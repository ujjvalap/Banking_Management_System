package com.sprint.bank.mapper;

import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccount() != null ? transaction.getAccount().getId() : null)
                .accountNumber(transaction.getAccount() != null ? transaction.getAccount().getAccountNumber() : null)
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .referenceId(transaction.getReferenceId())
                .timestamp(transaction.getTimestamp())
                .build();
    }
}
