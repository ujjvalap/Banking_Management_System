package com.sprint.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.audit.repository.AuditLogRepository;
import com.sprint.bank.auth.exception.InvalidTokenException;
import com.sprint.bank.auth.repository.EmailVerificationTokenRepository;
import com.sprint.bank.auth.repository.LoginAttemptRepository;
import com.sprint.bank.auth.repository.PasswordResetTokenRepository;
import com.sprint.bank.auth.repository.RefreshTokenRepository;
import com.sprint.bank.auth.service.EmailVerificationService;
import com.sprint.bank.auth.service.PasswordResetService;
import com.sprint.bank.common.constants.AppConstants;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.security.principal.CustomUserDetails;
import com.sprint.bank.transaction.dto.TransferRequest;
import com.sprint.bank.transaction.repository.TransactionRepository;
import com.sprint.bank.transaction.service.TransferService;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.enums.Role;
import com.sprint.bank.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BankingManagementSystemIntegrationTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CustomerProfileRepository customerProfileRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired
    private LoginAttemptRepository loginAttemptRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private EmailVerificationService emailVerificationService;
    @Autowired
    private TransferService transferService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;
    @Value("${app.jwt.issuer}")
    private String jwtIssuer;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void resetDatabase() {
        auditLogRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        customerProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void authenticationLifecycleRotationReuseDetectionLockoutAndLogoutWork() throws Exception {
        // 1. Register creates User + CustomerProfile with BCrypt hash & assigns CUSTOMER role
        String registerPayload = """
                {
                  "fullName":"Alex Morgan",
                  "email":"Alex.Morgan@example.com",
                  "password":"StrongPass123!",
                  "phone":"+91 9876543210",
                  "address":"12 Market Street",
                  "pan":"ABCDE1234F"
                }
                """;
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andExpect(cookie().httpOnly(AppConstants.REFRESH_TOKEN_COOKIE, true))
                .andReturn();

        User savedUser = userRepository.findByEmail("alex.morgan@example.com").orElseThrow();
        assertEquals(Role.CUSTOMER, savedUser.getRole());
        assertNotEquals("StrongPass123!", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("StrongPass123!", savedUser.getPassword()));
        assertTrue(customerProfileRepository.findByUserEmail("alex.morgan@example.com").isPresent());

        // 2. Duplicate email rejected with 409 Conflict
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isConflict());

        // 3. Login with wrong password fails with 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex.morgan@example.com","password":"WrongPassword1!"}
                                """))
                .andExpect(status().isUnauthorized());

        // 4. Successful login returns access/refresh tokens + HttpOnly cookies
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex.morgan@example.com","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(AppConstants.REFRESH_TOKEN_COOKIE, true))
                .andReturn();

        JsonNode loginData = readData(loginResult);
        String accessToken = loginData.get("accessToken").asText();
        String refreshToken1 = loginData.get("refreshToken").asText();

        // 5. /api/auth/me works
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("alex.morgan@example.com"));

        // 6. Refresh token rotation
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshedData = readData(refreshResult);
        String accessToken2 = refreshedData.get("accessToken").asText();
        String refreshToken2 = refreshedData.get("refreshToken").asText();
        assertNotEquals(refreshToken1, refreshToken2);

        // 7. Cookie-based refresh without CSRF fails with 403; with CSRF succeeds
        Cookie refreshCookie = refreshResult.getResponse().getCookie(AppConstants.REFRESH_TOKEN_COOKIE);
        assertNotNull(refreshCookie);
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isForbidden());

        Cookie xsrfCookie = refreshResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie);
        MvcResult cookieRefreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie, xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken3 = readData(cookieRefreshResult).get("accessToken").asText();
        String refreshToken3 = readData(cookieRefreshResult).get("refreshToken").asText();

        // 8. Reuse of old refreshToken1 returns 401 AND revokes entire family (refreshToken3 must also fail!)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken1)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken3)))
                .andExpect(status().isUnauthorized());

        // 9. Logout revokes access token immediately via tokenVersion & clears cookies
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken3))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AppConstants.REFRESH_TOKEN_COOKIE, 0));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken3))
                .andExpect(status().isUnauthorized());

        // 10. Repeated invalid logins lock the account (423 Locked)
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"alex.morgan@example.com","password":"WrongPassword1!"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex.morgan@example.com","password":"WrongPassword1!"}
                                """))
                .andExpect(status().isLocked());

        // Even correct password is blocked while locked
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex.morgan@example.com","password":"StrongPass123!"}
                                """))
                .andExpect(status().isLocked());
    }

    @Test
    void passwordResetAndEmailVerificationTokensAreSingleUse() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("casey@example.com", passwordEncoder.encode("OldStrong123!"), Role.CUSTOMER, false));
        customerProfileRepository.saveAndFlush(CustomerProfile.builder()
                .user(user).fullName("Casey").phone("9876543210").address("Street 1").pan("CASEY1234A").build());

        emailVerificationService.sendVerification(user);
        ArgumentCaptor<SimpleMailMessage> verifyCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.verify(mailSender).send(verifyCaptor.capture());
        String verifyToken = extractTokenFromMail(verifyCaptor.getValue().getText());

        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());
        assertTrue(userRepository.findById(user.getId()).orElseThrow().isEnabled());

        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isUnauthorized());

        Mockito.reset(mailSender);
        passwordResetService.requestReset("casey@example.com");
        ArgumentCaptor<SimpleMailMessage> resetCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.verify(mailSender).send(resetCaptor.capture());
        String resetToken = extractTokenFromMail(resetCaptor.getValue().getText());

        passwordResetService.reset(resetToken, "NewStrong456!");
        assertTrue(passwordEncoder.matches("NewStrong456!", userRepository.findById(user.getId()).orElseThrow().getPassword()));
        assertThrows(InvalidTokenException.class, () -> passwordResetService.reset(resetToken, "AnotherPass789!"));
    }

    @Test
    void endToEndBankingWorkflowSecurityAndAdminControlsWork() throws Exception {
        String priyaToken = registerCustomer("Priya Sharma", "priya@example.com", "StrongPass123!", "PRIYA1234A");
        String rohanToken = registerCustomer("Rohan Verma", "rohan@example.com", "StrongPass123!", "ROHAN5678B");

        // Open accounts (201 Created)
        JsonNode priyaSavings = openAccount(priyaToken, "SAVINGS");
        long priyaAccId = priyaSavings.get("id").asLong();
        String priyaAccNum = priyaSavings.get("accountNumber").asText();

        JsonNode rohanCurrent = openAccount(rohanToken, "CURRENT");
        long rohanAccId = rohanCurrent.get("id").asLong();
        String rohanAccNum = rohanCurrent.get("accountNumber").asText();

        // Ownership enforcement: Rohan cannot view, deposit into, or withdraw from Priya's account (403 Forbidden)
        mockMvc.perform(get("/api/accounts/" + priyaAccId)
                        .header("Authorization", "Bearer " + rohanToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/accounts/" + priyaAccId + "/deposit")
                        .header("Authorization", "Bearer " + rohanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isForbidden());

        // Invalid / expired JWT checks
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer malformed.jwt.token"))
                .andExpect(status().isUnauthorized());

        String expiredJwt = Jwts.builder()
                .subject("priya@example.com")
                .issuer(jwtIssuer)
                .claim("userId", 1L)
                .claim("tokenVersion", 0L)
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + expiredJwt))
                .andExpect(status().isUnauthorized());

        // Deposit, withdraw, insufficient balance
        mockMvc.perform(post("/api/accounts/" + priyaAccId + "/deposit")
                        .header("Authorization", "Bearer " + priyaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1500.00,\"description\":\"Salary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(1500.00));

        mockMvc.perform(post("/api/accounts/" + priyaAccId + "/withdraw")
                        .header("Authorization", "Bearer " + priyaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":300.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(1200.00));

        mockMvc.perform(post("/api/accounts/" + priyaAccId + "/withdraw")
                        .header("Authorization", "Bearer " + priyaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Insufficient")));

        // Transfer to Rohan
        MvcResult transferRes = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + priyaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":%d,"toAccountNumber":"%s","amount":450.00,"description":"Rent"}
                                """.formatted(priyaAccId, rohanAccNum)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.data.accountNumber").value(priyaAccNum))
                .andExpect(jsonPath("$.data.referenceId").isNotEmpty())
                .andReturn();

        String refId = readData(transferRes).get("referenceId").asText();
        var matchingRefTxs = transactionRepository.findByReferenceId(refId);
        assertEquals(2, matchingRefTxs.size());

        // Customer paginated & filtered statements
        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/accounts/" + priyaAccId + "/transactions")
                        .param("type", "TRANSFER_OUT")
                        .param("startDate", today)
                        .param("endDate", today)
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", "Bearer " + priyaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].referenceId").value(refId));

        // Customer cannot access Admin APIs
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + priyaToken))
                .andExpect(status().isForbidden());

        // Seed ADMIN and test Admin APIs
        userRepository.saveAndFlush(new User(
                "admin@northstarbank.com",
                passwordEncoder.encode("AdminStrong123!"),
                Role.ADMIN,
                true
        ));
        String adminToken = loginUser("admin@northstarbank.com", "AdminStrong123!");

        // Admin cannot hit customer-only accounts endpoint
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/admin/accounts/" + priyaAccId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(750.00));

        // Admin freezes Priya's account -> deposit rejected
        mockMvc.perform(put("/api/admin/accounts/" + priyaAccId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"));

        mockMvc.perform(post("/api/accounts/" + priyaAccId + "/deposit")
                        .header("Authorization", "Bearer " + priyaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("frozen")));

        // Cannot close account with positive balance
        mockMvc.perform(put("/api/admin/accounts/" + priyaAccId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest());

        // Admin filters transactions by type and date
        mockMvc.perform(get("/api/admin/transactions")
                        .param("type", "DEPOSIT")
                        .param("startDate", today)
                        .param("endDate", today)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].type").value("DEPOSIT"));

        // Public pages, Swagger UI, and static assets are accessible
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/auth/login")).andExpect(status().isOk());
        mockMvc.perform(get("/auth/register")).andExpect(status().isOk());
        mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/api.js")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());

        // Protected Thymeleaf pages redirect anonymous users and block wrong role
        mockMvc.perform(get("/customer/dashboard")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/dashboard")
                        .cookie(new Cookie(AppConstants.ACCESS_TOKEN_COOKIE, priyaToken))
                        .header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/customer/dashboard")
                        .cookie(new Cookie(AppConstants.ACCESS_TOKEN_COOKIE, priyaToken)))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentTransfersPreventDoubleSpending() throws Exception {
        String senderToken = registerCustomer("Concurrent Sender", "csender@example.com", "StrongPass123!", "CSEND1234A");
        String receiverToken = registerCustomer("Concurrent Receiver", "creceiver@example.com", "StrongPass123!", "CRECV1234B");

        JsonNode senderAcc = openAccount(senderToken, "SAVINGS");
        long senderId = senderAcc.get("id").asLong();
        JsonNode receiverAcc = openAccount(receiverToken, "SAVINGS");
        long receiverId = receiverAcc.get("id").asLong();
        String receiverNumber = receiverAcc.get("accountNumber").asText();

        mockMvc.perform(post("/api/accounts/" + senderId + "/deposit")
                        .header("Authorization", "Bearer " + senderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00}"))
                .andExpect(status().isOk());

        User senderUser = userRepository.findByEmail("csender@example.com").orElseThrow();
        CustomUserDetails principal = new CustomUserDetails(
                senderUser.getId(),
                senderUser.getEmail(),
                senderUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                true,
                senderUser.getTokenVersion(),
                true
        );

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                ready.countDown();
                try {
                    start.await();
                    transferService.transfer(TransferRequest.builder()
                            .fromAccountId(senderId)
                            .toAccountNumber(receiverNumber)
                            .amount(new BigDecimal("200.00"))
                            .description("Concurrent test")
                            .build());
                    successes.incrementAndGet();
                } catch (Exception ex) {
                    failures.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertEquals(5, successes.get(), "Exactly 5 transfers of 200 should succeed from 1000 balance");
        assertEquals(5, failures.get(), "Remaining 5 transfers must fail without overdraft");
        assertEquals(0, new BigDecimal("0.00").compareTo(
                accountRepository.findById(senderId).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(
                accountRepository.findById(receiverId).orElseThrow().getBalance()));
    }

    private String registerCustomer(String name, String email, String password, String pan) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"%s",
                                  "email":"%s",
                                  "password":"%s",
                                  "phone":"+91 9876543210",
                                  "address":"10 MG Road",
                                  "pan":"%s"
                                }
                                """.formatted(name, email, password, pan)))
                .andExpect(status().isCreated())
                .andReturn();
        return readData(result).get("accessToken").asText();
    }

    private String loginUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return readData(result).get("accessToken").asText();
    }

    private JsonNode openAccount(String token, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readData(result);
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String extractTokenFromMail(String text) {
        int idx = text.lastIndexOf("#token=");
        assertTrue(idx >= 0, "Expected mail body to include #token=");
        return text.substring(idx + "#token=".length()).trim();
    }
}
