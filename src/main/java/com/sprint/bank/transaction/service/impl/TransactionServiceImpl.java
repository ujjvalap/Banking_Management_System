package com.sprint.bank.transaction.service.impl;

import com.sprint.bank.account.entity.Account;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.common.constants.AppConstants;
import com.sprint.bank.common.dto.PageResponse;
import com.sprint.bank.exception.AccountNotFoundException;
import com.sprint.bank.exception.ResourceForbiddenException;
import com.sprint.bank.mapper.TransactionMapper;
import com.sprint.bank.security.SecurityUtils;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.enums.TransactionType;
import com.sprint.bank.transaction.repository.TransactionRepository;
import com.sprint.bank.transaction.service.TransactionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.transactionMapper = transactionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAccountStatement(
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            int page,
            int size) {
        Account account = accountRepository.findWithCustomerById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!account.getCustomer().getUser().getId().equals(currentUserId)) {
            throw new ResourceForbiddenException("You do not have permission to view statements for this account.");
        }

        validateDateRange(startDate, endDate);
        Pageable pageable = buildPageable(page, size);
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        return PageResponse.from(
                transactionRepository.findAccountStatement(accountId, type, start, end, pageable)
                        .map(transactionMapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAllTransactions(
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            int page,
            int size) {
        validateDateRange(startDate, endDate);
        Pageable pageable = buildPageable(page, size);
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        return PageResponse.from(
                transactionRepository.findFilteredTransactions(type, start, end, pageable)
                        .map(transactionMapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getMyRecentTransactions() {
        String email = SecurityUtils.getCurrentUserEmail();
        return transactionRepository.findTop20ByAccountCustomerUserEmailOrderByTimestampDescIdDesc(email)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getMyTransactionCount() {
        return transactionRepository.countByAccountCustomerUserEmail(SecurityUtils.getCurrentUserEmail());
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }
    }

    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? AppConstants.DEFAULT_PAGE_SIZE : Math.min(size, AppConstants.MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
