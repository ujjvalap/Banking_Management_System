package com.sprint.bank.admin;

import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.account.entity.Account;
import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.account.enums.AccountType;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.admin.service.AdminService;
import com.sprint.bank.admin.service.impl.AdminServiceImpl;
import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.exception.InvalidAccountStatusException;
import com.sprint.bank.mapper.AccountMapper;
import com.sprint.bank.mapper.CustomerMapper;
import com.sprint.bank.security.principal.CustomUserDetails;
import com.sprint.bank.transaction.repository.TransactionRepository;
import com.sprint.bank.transaction.service.TransactionService;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.enums.Role;
import com.sprint.bank.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class AdminServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionService transactionService;
    @Mock
    private AuditService auditService;

    private AdminServiceImpl adminService;
    private Account account;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
                userRepository,
                customerProfileRepository,
                accountRepository,
                transactionRepository,
                transactionService,
                new CustomerMapper(),
                new AccountMapper(),
                auditService
        );

        User customerUser = new User("cust@example.com", "hash", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(customerUser, "id", 21L);
        CustomerProfile profile = CustomerProfile.builder()
                .id(21L)
                .user(customerUser)
                .fullName("Customer One")
                .phone("1234567")
                .address("Address")
                .pan("ABCDE1234F")
                .build();

        account = new Account();
        account.setId(5L);
        account.setAccountNumber("NB111222333444");
        account.setType(AccountType.CURRENT);
        account.setBalance(new BigDecimal("150.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setCustomer(profile);

        CustomUserDetails adminPrincipal = new CustomUserDetails(
                1L, "admin@example.com", "hash",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), true, 0L, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getSummaryReturnsZeroWhenTotalBalanceSumIsNull() {
        when(userRepository.countByRole(Role.CUSTOMER)).thenReturn(3L);
        when(accountRepository.count()).thenReturn(5L);
        when(transactionRepository.count()).thenReturn(12L);
        when(accountRepository.sumAllBalances()).thenReturn(null);

        AdminService.AdminSummary summary = adminService.getSummary();
        assertEquals(3L, summary.totalCustomers());
        assertEquals(5L, summary.totalAccounts());
        assertEquals(12L, summary.totalTransactions());
        assertEquals(BigDecimal.ZERO, summary.totalBalance());
    }

    @Test
    void updateAccountStatusEnforcesLifecycleRules() {
        when(accountRepository.findWithLockById(5L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        // Freeze active account
        AccountResponse frozen = adminService.updateAccountStatus(5L, AccountStatus.FROZEN);
        assertEquals(AccountStatus.FROZEN, frozen.getStatus());

        // Cannot close account while balance > 0
        assertThrows(InvalidAccountStatusException.class,
                () -> adminService.updateAccountStatus(5L, AccountStatus.CLOSED));

        // Close succeeds once balance is 0
        account.setBalance(BigDecimal.ZERO);
        AccountResponse closed = adminService.updateAccountStatus(5L, AccountStatus.CLOSED);
        assertEquals(AccountStatus.CLOSED, closed.getStatus());

        // Cannot reopen a closed account
        assertThrows(InvalidAccountStatusException.class,
                () -> adminService.updateAccountStatus(5L, AccountStatus.ACTIVE));
    }
}
