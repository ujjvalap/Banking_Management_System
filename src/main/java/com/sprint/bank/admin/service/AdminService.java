package com.sprint.bank.admin.service;

import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.common.dto.PageResponse;
import com.sprint.bank.customer.dto.CustomerResponse;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AdminService {

    record AdminSummary(
            long totalCustomers,
            long totalAccounts,
            long totalTransactions,
            BigDecimal totalBalance
    ) {
    }

    AdminSummary getSummary();

    List<CustomerResponse> getAllCustomers();

    List<AccountResponse> getAllAccounts();

    AccountResponse getAccountById(Long accountId);

    AccountResponse updateAccountStatus(Long accountId, AccountStatus status);

    PageResponse<TransactionResponse> getTransactions(
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            int page,
            int size);
}
