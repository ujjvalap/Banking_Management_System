package com.sprint.bank.config;

import com.sprint.bank.account.entity.Account;
import com.sprint.bank.account.enums.AccountStatus;
import com.sprint.bank.account.enums.AccountType;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.common.util.AccountNumberGenerator;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.transaction.entity.Transaction;
import com.sprint.bank.transaction.enums.TransactionType;
import com.sprint.bank.transaction.repository.TransactionRepository;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.enums.Role;
import com.sprint.bank.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String customerPassword;

    public DataInitializer(
            UserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin-email:admin@northstarbank.com}") String adminEmail,
            @Value("${app.seed.admin-password:}") String adminPassword,
            @Value("${app.seed.customer-password:}") String customerPassword) {
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.customerPassword = customerPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank() || customerPassword == null || customerPassword.isBlank()) {
            logger.info("Seed passwords are not set; skipping development seed initialization.");
            return;
        }

        seedAdmin();
        if (userRepository.countByRole(Role.CUSTOMER) > 0) {
            return;
        }

        CustomerProfile aarav = seedCustomer(
                "Aarav Sharma",
                "aarav.sharma@northstarbank.com",
                "+91 9876543210",
                "14 MG Road, Bengaluru",
                "ABCDE1234F"
        );
        CustomerProfile priya = seedCustomer(
                "Priya Patel",
                "priya.patel@northstarbank.com",
                "+91 9876543211",
                "22 CG Road, Ahmedabad",
                "FGHIJ5678K"
        );

        Account aaravSavings = createAccount(aarav, AccountType.SAVINGS, new BigDecimal("18500.00"));
        Account aaravCurrent = createAccount(aarav, AccountType.CURRENT, new BigDecimal("42000.00"));
        Account priyaSavings = createAccount(priya, AccountType.SAVINGS, new BigDecimal("12500.00"));

        saveTransaction(aaravSavings, TransactionType.DEPOSIT, new BigDecimal("25000.00"),
                "Initial branch deposit", "TXN-SEED-DEP-001");
        saveTransaction(aaravSavings, TransactionType.WITHDRAW, new BigDecimal("4000.00"),
                "ATM cash withdrawal", "TXN-SEED-WDR-002");
        saveTransaction(aaravCurrent, TransactionType.DEPOSIT, new BigDecimal("42000.00"),
                "Business settlement deposit", "TXN-SEED-DEP-003");
        saveTransaction(priyaSavings, TransactionType.DEPOSIT, new BigDecimal("10000.00"),
                "Opening savings deposit", "TXN-SEED-DEP-004");

        String transferRef = "TXN-SEED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        saveTransaction(aaravSavings, TransactionType.TRANSFER_OUT, new BigDecimal("2500.00"),
                "Transferred to " + priyaSavings.getAccountNumber(), transferRef);
        saveTransaction(priyaSavings, TransactionType.TRANSFER_IN, new BigDecimal("2500.00"),
                "Received from " + aaravSavings.getAccountNumber(), transferRef);

        logger.info("Seeded development admin and 2 sample customer banking portfolios.");
    }

    private void seedAdmin() {
        String email = adminEmail.trim().toLowerCase();
        if (!userRepository.existsByEmail(email)) {
            User admin = new User(email, passwordEncoder.encode(adminPassword), Role.ADMIN, true);
            userRepository.save(admin);
        }
    }

    private CustomerProfile seedCustomer(String fullName, String email, String phone, String address, String pan) {
        User user = userRepository.save(new User(email, passwordEncoder.encode(customerPassword), Role.CUSTOMER, true));
        CustomerProfile profile = CustomerProfile.builder()
                .user(user)
                .fullName(fullName)
                .phone(phone)
                .address(address)
                .pan(pan)
                .build();
        return customerProfileRepository.save(profile);
    }

    private Account createAccount(CustomerProfile customer, AccountType type, BigDecimal balance) {
        Account account = new Account();
        String number;
        do {
            number = AccountNumberGenerator.generate();
        } while (accountRepository.existsByAccountNumber(number));
        account.setAccountNumber(number);
        account.setType(type);
        account.setBalance(balance);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCustomer(customer);
        return accountRepository.save(account);
    }

    private void saveTransaction(
            Account account,
            TransactionType type,
            BigDecimal amount,
            String description,
            String referenceId) {
        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setDescription(description);
        tx.setReferenceId(referenceId);
        transactionRepository.save(tx);
    }
}
