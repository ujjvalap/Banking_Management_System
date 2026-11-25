package com.sprint.bank.transaction;

import com.sprint.bank.account.entity.Account;
import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.account.enums.AccountType;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.exception.AccountNotFoundException;
import com.sprint.bank.exception.InsufficientBalanceException;
import com.sprint.bank.exception.InvalidAccountStatusException;
import com.sprint.bank.mapper.TransactionMapper;
import com.sprint.bank.security.principal.CustomUserDetails;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.dto.TransferRequest;
import com.sprint.bank.transaction.entity.Transaction;
import com.sprint.bank.transaction.enums.TransactionType;
import com.sprint.bank.transaction.repository.TransactionRepository;
import com.sprint.bank.transaction.service.impl.TransferServiceImpl;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuditService auditService;

    private TransferServiceImpl transferService;

    private Account sender;
    private Account receiver;

    @BeforeEach
    void setUp() {
        transferService = new TransferServiceImpl(
                accountRepository,
                transactionRepository,
                new TransactionMapper(),
                auditService
        );

        User user1 = new User("sender@example.com", "hash", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(user1, "id", 10L);
        CustomerProfile cp1 = CustomerProfile.builder().id(10L).user(user1).fullName("Sender").phone("1234567").address("Addr 1").pan("ABCDE1234F").build();

        User user2 = new User("receiver@example.com", "hash", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(user2, "id", 20L);
        CustomerProfile cp2 = CustomerProfile.builder().id(20L).user(user2).fullName("Receiver").phone("7654321").address("Addr 2").pan("FGHIJ5678K").build();

        sender = new Account();
        sender.setId(1L);
        sender.setAccountNumber("NB100000000001");
        sender.setType(AccountType.SAVINGS);
        sender.setBalance(new BigDecimal("1000.00"));
        sender.setStatus(AccountStatus.ACTIVE);
        sender.setCustomer(cp1);

        receiver = new Account();
        receiver.setId(2L);
        receiver.setAccountNumber("NB100000000002");
        receiver.setType(AccountType.CURRENT);
        receiver.setBalance(new BigDecimal("250.00"));
        receiver.setStatus(AccountStatus.ACTIVE);
        receiver.setCustomer(cp2);

        CustomUserDetails principal = new CustomUserDetails(
                10L,
                "sender@example.com",
                "hash",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                true,
                0L,
                true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulTransferUpdatesBothBalancesWithSharedReferenceId() {
        when(accountRepository.findOwnerUserIdById(1L)).thenReturn(Optional.of(10L));
        when(accountRepository.findIdByAccountNumber("NB100000000002")).thenReturn(Optional.of(2L));
        when(accountRepository.findWithLockById(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findWithLockById(2L)).thenReturn(Optional.of(receiver));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(1L)
                .toAccountNumber("NB100000000002")
                .amount(new BigDecimal("350.00"))
                .description("Rent")
                .build();

        TransactionResponse response = transferService.transfer(request);

        assertEquals(new BigDecimal("650.00"), sender.getBalance());
        assertEquals(new BigDecimal("600.00"), receiver.getBalance());
        assertEquals(TransactionType.TRANSFER_OUT, response.getType());

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txCaptor.capture());
        List<Transaction> saved = txCaptor.getAllValues();
        assertEquals(saved.get(0).getReferenceId(), saved.get(1).getReferenceId());
        assertEquals(TransactionType.TRANSFER_OUT, saved.get(0).getType());
        assertEquals(TransactionType.TRANSFER_IN, saved.get(1).getType());
    }

    @Test
    void transferFailsOnInsufficientBalance() {
        when(accountRepository.findOwnerUserIdById(1L)).thenReturn(Optional.of(10L));
        when(accountRepository.findIdByAccountNumber("NB100000000002")).thenReturn(Optional.of(2L));
        when(accountRepository.findWithLockById(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findWithLockById(2L)).thenReturn(Optional.of(receiver));

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(1L)
                .toAccountNumber("NB100000000002")
                .amount(new BigDecimal("5000.00"))
                .build();

        assertThrows(InsufficientBalanceException.class, () -> transferService.transfer(request));
        assertEquals(new BigDecimal("1000.00"), sender.getBalance());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferFailsOnSameAccount() {
        when(accountRepository.findOwnerUserIdById(1L)).thenReturn(Optional.of(10L));
        when(accountRepository.findIdByAccountNumber("NB100000000001")).thenReturn(Optional.of(1L));

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(1L)
                .toAccountNumber("NB100000000001")
                .amount(new BigDecimal("100.00"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> transferService.transfer(request));
    }

    @Test
    void transferFailsOnInvalidReceiverOrFrozenStatus() {
        when(accountRepository.findOwnerUserIdById(1L)).thenReturn(Optional.of(10L));
        when(accountRepository.findIdByAccountNumber("INVALID999")).thenReturn(Optional.empty());

        TransferRequest missingReceiver = TransferRequest.builder()
                .fromAccountId(1L)
                .toAccountNumber("INVALID999")
                .amount(new BigDecimal("100.00"))
                .build();
        assertThrows(AccountNotFoundException.class, () -> transferService.transfer(missingReceiver));

        receiver.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findIdByAccountNumber("NB100000000002")).thenReturn(Optional.of(2L));
        when(accountRepository.findWithLockById(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findWithLockById(2L)).thenReturn(Optional.of(receiver));

        TransferRequest frozenReceiver = TransferRequest.builder()
                .fromAccountId(1L)
                .toAccountNumber("NB100000000002")
                .amount(new BigDecimal("100.00"))
                .build();
        assertThrows(InvalidAccountStatusException.class, () -> transferService.transfer(frozenReceiver));
    }

    @Test
    void transferFailsWhenNotOwnerOfSenderAccount() {
        when(accountRepository.findOwnerUserIdById(1L)).thenReturn(Optional.of(999L));

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(1L)
                .toAccountNumber("NB100000000002")
                .amount(new BigDecimal("50.00"))
                .build();

        assertThrows(com.sprint.bank.exception.ResourceForbiddenException.class,
                () -> transferService.transfer(request));
        verify(accountRepository, never()).findWithLockById(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferPropagatesErrorWithoutAuditingWhenTransactionSaveFails() {
        when(accountRepository.findOwnerUserIdById(1L)).thenReturn(Optional.of(10L));
        when(accountRepository.findIdByAccountNumber("NB100000000002")).thenReturn(Optional.of(2L));
        when(accountRepository.findWithLockById(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findWithLockById(2L)).thenReturn(Optional.of(receiver));
        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new IllegalStateException("Simulated ledger failure"));

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(1L)
                .toAccountNumber("NB100000000002")
                .amount(new BigDecimal("100.00"))
                .build();

        assertThrows(IllegalStateException.class, () -> transferService.transfer(request));
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }
}
