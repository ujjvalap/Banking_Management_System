package com.sprint.bank.transaction.service.impl;

import com.sprint.bank.account.entity.Account;
import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.exception.AccountNotFoundException;
import com.sprint.bank.exception.InsufficientBalanceException;
import com.sprint.bank.exception.InvalidAccountStatusException;
import com.sprint.bank.exception.ResourceForbiddenException;
import com.sprint.bank.mapper.TransactionMapper;
import com.sprint.bank.security.SecurityUtils;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.dto.TransferRequest;
import com.sprint.bank.transaction.entity.Transaction;
import com.sprint.bank.transaction.enums.TransactionType;
import com.sprint.bank.transaction.repository.TransactionRepository;
import com.sprint.bank.transaction.service.TransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final AuditService auditService;

    public TransferServiceImpl(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            TransactionMapper transactionMapper,
            AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Transfer amount cannot have more than 2 decimal places.");
        }

        String targetAccountNumber = request.getToAccountNumber() == null
                ? ""
                : request.getToAccountNumber().trim().toUpperCase(Locale.ROOT);

        Long senderId = request.getFromAccountId();
        if (senderId == null) {
            throw new IllegalArgumentException("Sender account ID is required.");
        }

        Long senderOwnerUserId = accountRepository.findOwnerUserIdById(senderId)
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found."));

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!senderOwnerUserId.equals(currentUserId)) {
            throw new ResourceForbiddenException("You do not have permission to transfer from this account.");
        }

        Long receiverId = accountRepository.findIdByAccountNumber(targetAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Receiver account not found."));

        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }

        Long firstId = Math.min(senderId, receiverId);
        Long secondId = Math.max(senderId, receiverId);

        Account firstLocked = accountRepository.findWithLockById(firstId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
        Account secondLocked = accountRepository.findWithLockById(secondId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));

        Account sender = senderId.equals(firstId) ? firstLocked : secondLocked;
        Account receiver = receiverId.equals(firstId) ? firstLocked : secondLocked;

        if (sender.getStatus() != AccountStatus.ACTIVE || receiver.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidAccountStatusException("Both sender and receiver accounts must be active.");
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for transfer.");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        accountRepository.save(sender);
        accountRepository.save(receiver);

        String referenceId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        String note = (request.getDescription() != null && !request.getDescription().isBlank())
                ? " (" + request.getDescription().trim() + ")"
                : "";

        Transaction debit = new Transaction();
        debit.setAccount(sender);
        debit.setAmount(amount);
        debit.setType(TransactionType.TRANSFER_OUT);
        debit.setReferenceId(referenceId);
        debit.setDescription("Transferred to " + receiver.getAccountNumber() + note);

        Transaction credit = new Transaction();
        credit.setAccount(receiver);
        credit.setAmount(amount);
        credit.setType(TransactionType.TRANSFER_IN);
        credit.setReferenceId(referenceId);
        credit.setDescription("Received from " + sender.getAccountNumber() + note);

        Transaction savedDebit = transactionRepository.save(debit);
        transactionRepository.save(credit);

        auditService.log(SecurityUtils.getCurrentUserEmail(), "FUNDS_TRANSFERRED", "Transaction",
                referenceId,
                "Transferred " + amount + " from " + sender.getAccountNumber() + " to " + receiver.getAccountNumber());

        return transactionMapper.toResponse(savedDebit);
    }
}
