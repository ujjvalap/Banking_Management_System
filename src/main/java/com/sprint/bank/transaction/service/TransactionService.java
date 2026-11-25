package com.sprint.bank.transaction.service;

import com.sprint.bank.common.dto.PageResponse;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.enums.TransactionType;

import java.time.LocalDate;
import java.util.List;

public interface TransactionService {

    PageResponse<TransactionResponse> getAccountStatement(
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            int page,
            int size);

    PageResponse<TransactionResponse> getAllTransactions(
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            int page,
            int size);

    List<TransactionResponse> getMyRecentTransactions();

    long getMyTransactionCount();
}
