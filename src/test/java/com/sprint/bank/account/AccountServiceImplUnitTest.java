package com.sprint.bank.account;

import com.sprint.bank.account.dto.AccountRequest;
import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.account.entity.Account;
import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.account.enums.AccountType;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.account.service.impl.AccountServiceImpl;
import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.exception.InsufficientBalanceException;
import com.sprint.bank.exception.InvalidAccountStatusException;
import com.sprint.bank.exception.ResourceForbiddenException;
import com.sprint.bank.mapper.AccountMapper;
import com.sprint.bank.security.principal.CustomUserDetails;
import com.sprint.bank.transaction.dto.DepositRequest;
import com.sprint.bank.transaction.dto.WithdrawRequest;
import com.sprint.bank.transaction.entity.Transaction;
import com.sprint.bank.transaction.enums.TransactionType;
import com.sprint.bank.transaction.repository.TransactionRepository;
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
class AccountServiceImplUnitTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuditService auditService;

    private AccountServiceImpl accountService;
    private CustomerProfile profile;
    private Account activeAccount;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(
                accountRepository,
                customerProfileRepository,
                transactionRepository,
                new AccountMapper(),
                auditService
        );

        User owner = new User("owner@example.com", "hash", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(owner, "id", 10L);
        profile = CustomerProfile.builder()
                .id(10L)
                .user(owner)
                .fullName("Owner Customer")
                .phone("+15551234567")
                .address("123 Main St")
                .pan("ABCDE1234F")
                .build();

        activeAccount = new Account();
        activeAccount.setId(100L);
        activeAccount.setAccountNumber("NB123456789012");
        activeAccount.setType(AccountType.SAVINGS);
        activeAccount.setBalance(new BigDecimal("500.00"));
        activeAccount.setStatus(AccountStatus.ACTIVE);
        activeAccount.setCustomer(profile);

        CustomUserDetails principal = new CustomUserDetails(
                10L, "owner@example.com", "hash",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), true, 0L, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openAccountCreatesActiveZeroBalanceAccount() {
        when(customerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(55L);
            return a;
        });

        AccountResponse response = accountService.openAccount(new AccountRequest(AccountType.SAVINGS));

        assertEquals(55L, response.getId());
        assertEquals(AccountType.SAVINGS, response.getType());
        assertEquals(AccountStatus.ACTIVE, response.getStatus());
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertTrue(response.getAccountNumber().startsWith("NB"));
    }

    @Test
    void depositUpdatesBalanceAndPersistsDepositTransaction() {
        when(accountRepository.findWithLockById(100L)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.deposit(
                100L, new DepositRequest(new BigDecimal("250.50"), "Salary"));

        assertEquals(new BigDecimal("750.50"), response.getBalance());

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertEquals(TransactionType.DEPOSIT, txCaptor.getValue().getType());
        assertEquals(new BigDecimal("250.50"), txCaptor.getValue().getAmount());
        assertEquals("Salary", txCaptor.getValue().getDescription());
    }

    @Test
    void depositRejectsNonPositiveOrExcessPrecisionAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> accountService.deposit(100L, new DepositRequest(BigDecimal.ZERO, "Zero")));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.deposit(100L, new DepositRequest(new BigDecimal("-10.00"), "Negative")));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.deposit(100L, new DepositRequest(new BigDecimal("10.123"), "3 decimals")));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void withdrawRejectsInsufficientBalanceAndInactiveStatuses() {
        when(accountRepository.findWithLockById(100L)).thenReturn(Optional.of(activeAccount));

        assertThrows(InsufficientBalanceException.class,
                () -> accountService.withdraw(100L, new WithdrawRequest(new BigDecimal("999.00"), "Too much")));
        verify(transactionRepository, never()).save(any());

        activeAccount.setStatus(AccountStatus.FROZEN);
        assertThrows(InvalidAccountStatusException.class,
                () -> accountService.withdraw(100L, new WithdrawRequest(new BigDecimal("10.00"), "Frozen")));

        activeAccount.setStatus(AccountStatus.CLOSED);
        assertThrows(InvalidAccountStatusException.class,
                () -> accountService.deposit(100L, new DepositRequest(new BigDecimal("10.00"), "Closed")));
    }

    @Test
    void nonOwnerCannotViewOrOperateOnAccount() {
        User otherUser = new User("stranger@example.com", "hash", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(otherUser, "id", 99L);
        CustomerProfile otherProfile = CustomerProfile.builder()
                .id(99L).user(otherUser).fullName("Stranger").phone("7777777").address("Elsewhere").pan("XYZAB9999K").build();
        activeAccount.setCustomer(otherProfile);

        when(accountRepository.findWithCustomerById(100L)).thenReturn(Optional.of(activeAccount));
        assertThrows(ResourceForbiddenException.class, () -> accountService.getAccount(100L));

        when(accountRepository.findWithLockById(100L)).thenReturn(Optional.of(activeAccount));
        assertThrows(ResourceForbiddenException.class,
                () -> accountService.withdraw(100L, new WithdrawRequest(new BigDecimal("10.00"), "Unauthorized")));
    }
}
