package com.sprint.bank.account.service.impl;

import com.sprint.bank.account.dto.AccountRequest;
import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.account.entity.Account;
import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.account.service.AccountService;
import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.common.util.AccountNumberGenerator;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.exception.AccountNotFoundException;
import com.sprint.bank.exception.InsufficientBalanceException;
import com.sprint.bank.exception.InvalidAccountStatusException;
import com.sprint.bank.exception.ResourceForbiddenException;
import com.sprint.bank.exception.ResourceNotFoundException;
import com.sprint.bank.mapper.AccountMapper;
import com.sprint.bank.security.SecurityUtils;
import com.sprint.bank.transaction.dto.DepositRequest;
import com.sprint.bank.transaction.dto.WithdrawRequest;
import com.sprint.bank.transaction.entity.Transaction;
import com.sprint.bank.transaction.enums.TransactionType;
import com.sprint.bank.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final TransactionRepository transactionRepository;
    private final AccountMapper accountMapper;
    private final AuditService auditService;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            CustomerProfileRepository customerProfileRepository,
            TransactionRepository transactionRepository,
            AccountMapper accountMapper,
            AuditService auditService) {
        this.accountRepository = accountRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.transactionRepository = transactionRepository;
        this.accountMapper = accountMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public AccountResponse openAccount(AccountRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String email = SecurityUtils.getCurrentUserEmail();

        CustomerProfile customer = customerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found."));

        Account account = new Account();
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setType(request.type());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCustomer(customer);

        Account saved = accountRepository.save(account);
        auditService.log(email, "ACCOUNT_OPENED", "Account",
                String.valueOf(saved.getId()),
                "Opened " + saved.getType() + " account " + saved.getAccountNumber());

        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts() {
        String email = SecurityUtils.getCurrentUserEmail();
        return accountRepository.findByCustomerUserEmailOrderByCreatedAtDesc(email).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        Account account = accountRepository.findWithCustomerById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
        verifyOwnership(account);
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public AccountResponse deposit(Long accountId, DepositRequest request) {
        BigDecimal amount = request.getAmount();
        validateMonetaryAmount(amount);

        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
        verifyOwnership(account);
        ensureActive(account);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        String referenceId = generateReferenceId();
        String description = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription().trim()
                : "Cash deposit to " + account.getAccountNumber();

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setReferenceId(referenceId);
        transactionRepository.save(transaction);

        auditService.log(SecurityUtils.getCurrentUserEmail(), "ACCOUNT_DEPOSIT", "Account",
                String.valueOf(account.getId()),
                "Deposited " + amount + " into " + account.getAccountNumber() + " (ref " + referenceId + ")");

        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public AccountResponse withdraw(Long accountId, WithdrawRequest request) {
        BigDecimal amount = request.getAmount();
        validateMonetaryAmount(amount);

        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
        verifyOwnership(account);
        ensureActive(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for withdrawal.");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        String referenceId = generateReferenceId();
        String description = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription().trim()
                : "Cash withdrawal from " + account.getAccountNumber();

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(TransactionType.WITHDRAW);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setReferenceId(referenceId);
        transactionRepository.save(transaction);

        auditService.log(SecurityUtils.getCurrentUserEmail(), "ACCOUNT_WITHDRAW", "Account",
                String.valueOf(account.getId()),
                "Withdrew " + amount + " from " + account.getAccountNumber() + " (ref " + referenceId + ")");

        return accountMapper.toResponse(account);
    }

    private void verifyOwnership(Account account) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!account.getCustomer().getUser().getId().equals(currentUserId)) {
            throw new ResourceForbiddenException("You do not have permission to access this account.");
        }
    }

    private void ensureActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidAccountStatusException(
                    "Account is " + account.getStatus().name().toLowerCase(Locale.ROOT)
                            + " and cannot process transactions.");
        }
    }

    private void validateMonetaryAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimal places.");
        }
    }

    private String generateUniqueAccountNumber() {
        String candidate;
        do {
            candidate = AccountNumberGenerator.generate();
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }

    private String generateReferenceId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }
}
