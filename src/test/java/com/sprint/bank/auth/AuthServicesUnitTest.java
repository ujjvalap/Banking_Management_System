package com.sprint.bank.auth;

import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.auth.dto.AuthResponse;
import com.sprint.bank.auth.dto.LoginRequest;
import com.sprint.bank.auth.dto.RegisterRequest;
import com.sprint.bank.auth.dto.TokenResponse;
import com.sprint.bank.auth.entity.RefreshToken;
import com.sprint.bank.auth.exception.AccountLockedException;
import com.sprint.bank.auth.exception.InvalidCredentialsException;
import com.sprint.bank.auth.exception.InvalidTokenException;
import com.sprint.bank.auth.repository.RefreshTokenRepository;
import com.sprint.bank.auth.service.LoginAttemptService;
import com.sprint.bank.auth.service.LoginService;
import com.sprint.bank.auth.service.PasswordService;
import com.sprint.bank.auth.service.RefreshTokenService;
import com.sprint.bank.auth.service.RegistrationService;
import com.sprint.bank.auth.service.TokenRevocationService;
import com.sprint.bank.common.util.TokenCryptoUtil;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.exception.DuplicateEmailException;
import com.sprint.bank.mapper.UserMapper;
import com.sprint.bank.security.jwt.JwtService;
import com.sprint.bank.security.principal.CustomUserDetails;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.enums.Role;
import com.sprint.bank.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServicesUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RefreshTokenService refreshTokenServiceMock;
    @Mock
    private TokenRevocationService tokenRevocationService;

    @Test
    void registrationNormalizesEmailAndPanAndHashesPassword() {
        RegistrationService service = new RegistrationService(
                userRepository, customerProfileRepository, passwordEncoder, auditService, false);

        RegisterRequest request = new RegisterRequest(
                "  Aarav Sharma ",
                " AARAV@Example.COM ",
                "StrongPass#2026!",
                " +91-9876543210 ",
                " 22 MG Road, Bengaluru ",
                " abcde1234f "
        );

        when(userRepository.existsByEmail("aarav@example.com")).thenReturn(false);
        when(customerProfileRepository.existsByPan("ABCDE1234F")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass#2026!")).thenReturn("encoded-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 11L);
            return u;
        });
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        User registered = service.register(request);

        assertEquals("aarav@example.com", registered.getEmail());
        assertEquals("encoded-hash", registered.getPassword());
        assertEquals(Role.CUSTOMER, registered.getRole());
        assertTrue(registered.isEnabled());
        assertNotNull(registered.getCustomerProfile());
        assertEquals("ABCDE1234F", registered.getCustomerProfile().getPan());
        verify(auditService).log(eq("aarav@example.com"), eq("CUSTOMER_REGISTERED"), eq("User"), eq("11"), anyString());
    }

    @Test
    void registrationRejectsDuplicateEmailOrDuplicatePanOrOversizedPassword() {
        RegistrationService service = new RegistrationService(
                userRepository, customerProfileRepository, passwordEncoder, auditService, false);

        RegisterRequest request = new RegisterRequest(
                "Dup User", "dup@example.com", "StrongPass#2026!",
                "+15551234567", "123 Street", "ABCDE1234F");

        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> service.register(request));

        when(userRepository.existsByEmail("dup@example.com")).thenReturn(false);
        when(customerProfileRepository.existsByPan("ABCDE1234F")).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> service.register(request));

        RegisterRequest longPassword = new RegisterRequest(
                "Long Pass", "long@example.com", "A1!a".repeat(20),
                "+15551234567", "123 Street", "ZYXWV9876A");
        assertThrows(IllegalArgumentException.class, () -> service.register(longPassword));
    }

    @Test
    void loginHandlesSuccessAndLockoutAfterFailedAttempts() {
        LoginService service = new LoginService(
                authenticationManager,
                loginAttemptService,
                jwtService,
                refreshTokenServiceMock,
                userRepository,
                new UserMapper(),
                auditService
        );

        when(loginAttemptService.isLocked("locked@example.com")).thenReturn(true);
        assertThrows(AccountLockedException.class,
                () -> service.login(new LoginRequest("LOCKED@example.com", "StrongPass#2026!")));
        verifyNoInteractions(authenticationManager);

        when(loginAttemptService.isLocked("bad@example.com")).thenReturn(false, true);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        assertThrows(AccountLockedException.class,
                () -> service.login(new LoginRequest("bad@example.com", "WrongPass#123")));
        verify(loginAttemptService).record("bad@example.com", false);

        User user = new User("ok@example.com", "hash", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(user, "id", 5L);
        CustomerProfile profile = CustomerProfile.builder()
                .id(5L).user(user).fullName("Ok User").phone("1234567").address("Addr").pan("ABCDE1234F").build();
        user.setCustomerProfile(profile);

        CustomUserDetails principal = new CustomUserDetails(
                5L, "ok@example.com", "hash",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), true, 0L, true);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(loginAttemptService.isLocked("ok@example.com")).thenReturn(false);
        doReturn(auth).when(authenticationManager).authenticate(any());
        when(userRepository.findWithProfileById(5L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(principal)).thenReturn("jwt-access-token");
        when(jwtService.getAccessExpiration()).thenReturn(900000L);
        when(refreshTokenServiceMock.create(5L)).thenReturn("raw-refresh-token");

        AuthResponse response = service.login(new LoginRequest("OK@example.com", "StrongPass#2026!"));
        assertEquals("jwt-access-token", response.accessToken());
        assertEquals("raw-refresh-token", response.refreshToken());
        assertEquals(900L, response.expiresIn());
        assertEquals("ok@example.com", response.user().email());
        verify(loginAttemptService).record("ok@example.com", true);
    }

    @Test
    void refreshTokenRotationPreservesFamilyAndRevokesFamilyOnReuse() {
        RefreshTokenService service = new RefreshTokenService(
                refreshTokenRepository, userRepository, jwtService, loginAttemptService, auditService, 604800000L);

        User user = new User("user@example.com", "hash", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(user, "id", 7L);

        String raw = "sample-raw-refresh-token";
        String hash = TokenCryptoUtil.sha256Hex(raw);
        RefreshToken stored = new RefreshToken(user, hash, Instant.now().plusSeconds(3600), "family-123");

        when(refreshTokenRepository.findWithLockByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(loginAttemptService.isLocked("user@example.com")).thenReturn(false);
        when(jwtService.generateAccessToken(any(CustomUserDetails.class))).thenReturn("rotated-access");
        when(jwtService.getAccessExpiration()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse rotated = service.rotate(raw);
        assertTrue(stored.isRevoked());
        assertEquals("rotated-access", rotated.accessToken());
        assertNotNull(rotated.refreshToken());

        ArgumentCaptor<RefreshToken> savedCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(savedCaptor.capture());
        assertEquals("family-123", savedCaptor.getValue().getFamilyId());

        // Simulate replay of the already-revoked token
        RefreshToken activeChild = new RefreshToken(user, "child-hash", Instant.now().plusSeconds(3600), "family-123");
        when(refreshTokenRepository.findAllByFamilyId("family-123")).thenReturn(List.of(stored, activeChild));

        assertThrows(InvalidTokenException.class, () -> service.rotate(raw));
        assertTrue(activeChild.isRevoked());
        verify(auditService).log(eq("user@example.com"), eq("REFRESH_TOKEN_REUSE_DETECTED"), any(), eq("family-123"), any());
    }

    @Test
    void passwordChangeValidatesOldPasswordEncodesNewAndRevokesSessions() {
        PasswordService service = new PasswordService(
                userRepository, passwordEncoder, tokenRevocationService, auditService);

        User user = new User("pw@example.com", "old-hash", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(user, "id", 15L);

        when(userRepository.findById(15L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongOld#1234", "old-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.change(15L, "WrongOld#1234", "NewStrong#2026"));

        when(passwordEncoder.matches("OldStrong#2026", "old-hash")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.change(15L, "OldStrong#2026", "OldStrong#2026"));

        when(passwordEncoder.matches("NewStrong#2026", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewStrong#2026")).thenReturn("new-hash");

        service.change(15L, "OldStrong#2026", "NewStrong#2026");

        assertEquals("new-hash", user.getPassword());
        verify(tokenRevocationService).revokeAll(15L);
        verify(auditService).log(eq("pw@example.com"), eq("PASSWORD_CHANGED"), eq("User"), eq("15"), anyString());
    }
}
