package com.sprint.bank.admin.service.impl;

import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.account.entity.Account;
import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.admin.service.AdminService;
import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.common.dto.PageResponse;
import com.sprint.bank.customer.dto.CustomerResponse;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.exception.AccountNotFoundException;
import com.sprint.bank.exception.InvalidAccountStatusException;
import com.sprint.bank.mapper.AccountMapper;
import com.sprint.bank.mapper.CustomerMapper;
import com.sprint.bank.security.SecurityUtils;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.enums.TransactionType;
import com.sprint.bank.transaction.repository.TransactionRepository;
import com.sprint.bank.transaction.service.TransactionService;
import com.sprint.bank.user.enums.Role;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final CustomerMapper customerMapper;
    private final AccountMapper accountMapper;
    private final AuditService auditService;

    public AdminServiceImpl(
            UserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            TransactionService transactionService,
            CustomerMapper customerMapper,
            AccountMapper accountMapper,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.customerMapper = customerMapper;
        this.accountMapper = accountMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSummary getSummary() {
        long totalCustomers = userRepository.countByRole(Role.CUSTOMER);
        long totalAccounts = accountRepository.count();
        long totalTransactions = transactionRepository.count();
        BigDecimal totalBalance = accountRepository.sumAllBalances();
        return new AdminSummary(
                totalCustomers,
                totalAccounts,
                totalTransactions,
                totalBalance == null ? BigDecimal.ZERO : totalBalance
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerProfileRepository.findAllCustomersWithUser().stream()
                .map(profile -> customerMapper.toResponse(
                        profile,
                        (int) accountRepository.countByCustomerId(profile.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAllByOrderByIdDesc().stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long accountId) {
        Account account = accountRepository.findWithCustomerById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public AccountResponse updateAccountStatus(Long accountId, AccountStatus targetStatus) {
        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));

        if (account.getStatus() == AccountStatus.CLOSED && targetStatus != AccountStatus.CLOSED) {
            throw new InvalidAccountStatusException("A closed account cannot be reopened.");
        }
        if (targetStatus == AccountStatus.CLOSED && account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new InvalidAccountStatusException("Cannot close an account with a positive balance.");
        }

        AccountStatus previousStatus = account.getStatus();
        account.setStatus(targetStatus);
        Account saved = accountRepository.save(account);

        auditService.log(SecurityUtils.getCurrentUserEmail(), "ACCOUNT_STATUS_CHANGED", "Account",
                String.valueOf(saved.getId()),
                "Account " + saved.getAccountNumber() + " status changed from " + previousStatus + " to " + targetStatus);

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactions(
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            int page,
            int size) {
        return transactionService.getAllTransactions(startDate, endDate, type, page, size);
    }
}
