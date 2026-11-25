package com.sprint.bank.auth.service;

import com.sprint.bank.auth.dto.AuthResponse;
import com.sprint.bank.auth.dto.LoginRequest;
import com.sprint.bank.auth.dto.RegisterRequest;
import com.sprint.bank.common.constants.AppConstants;
import com.sprint.bank.exception.ResourceNotFoundException;
import com.sprint.bank.mapper.UserMapper;
import com.sprint.bank.security.principal.CustomUserDetails;
import com.sprint.bank.user.dto.UserResponse;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final UserMapper userMapper;

    public AuthService(
            RegistrationService registrationService,
            LoginService loginService,
            UserRepository userRepository,
            EmailVerificationService emailVerificationService,
            UserMapper userMapper) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.userRepository = userRepository;
        this.emailVerificationService = emailVerificationService;
        this.userMapper = userMapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User user = registrationService.register(request);
        if (!user.isEnabled()) {
            emailVerificationService.sendVerification(user);
            return AuthResponse.verificationRequired(userMapper.toResponse(user));
        }
        return loginService.issueTokens(toPrincipal(user), user);
    }

    public AuthResponse login(LoginRequest request) {
        return loginService.login(request);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findWithProfileByEmail(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        return userMapper.toResponse(user);
    }

    private CustomUserDetails toPrincipal(User user) {
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(AppConstants.ROLE_PREFIX + user.getRole().name())),
                user.isEnabled(),
                user.getTokenVersion(),
                true
        );
    }
}
