package com.sprint.bank.auth.service;

import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.auth.dto.AuthResponse;
import com.sprint.bank.auth.dto.LoginRequest;
import com.sprint.bank.auth.exception.AccountLockedException;
import com.sprint.bank.auth.exception.InvalidCredentialsException;
import com.sprint.bank.mapper.UserMapper;
import com.sprint.bank.security.jwt.JwtService;
import com.sprint.bank.security.principal.CustomUserDetails;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

    public LoginService(
            AuthenticationManager authenticationManager,
            LoginAttemptService loginAttemptService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            UserMapper userMapper,
            AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.loginAttemptService = loginAttemptService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (loginAttemptService.isLocked(email)) {
            throw new AccountLockedException();
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
            loginAttemptService.record(email, true);

            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            User user = userRepository.findWithProfileById(principal.getId())
                    .orElseThrow(InvalidCredentialsException::new);

            auditService.log(email, "USER_LOGIN", "User",
                    String.valueOf(principal.getId()), "Successful authentication");

            return issueTokens(principal, user);
        } catch (LockedException exception) {
            throw new AccountLockedException();
        } catch (BadCredentialsException | DisabledException exception) {
            loginAttemptService.record(email, false);
            if (loginAttemptService.isLocked(email)) {
                auditService.log(email, "ACCOUNT_LOCKED", "User", email, "Locked after failed login threshold");
                throw new AccountLockedException();
            }
            throw new InvalidCredentialsException();
        }
    }

    public AuthResponse issueTokens(CustomUserDetails principal, User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(principal),
                refreshTokenService.create(principal.getId()),
                "Bearer",
                jwtService.getAccessExpiration() / 1000,
                userMapper.toResponse(user)
        );
    }
}
