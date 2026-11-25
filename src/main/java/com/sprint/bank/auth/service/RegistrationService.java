package com.sprint.bank.auth.service;

import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.auth.dto.RegisterRequest;
import com.sprint.bank.common.constants.AppConstants;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.exception.DuplicateEmailException;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.enums.Role;
import com.sprint.bank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final boolean requireEmailVerification;

    public RegistrationService(
            UserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Value("${app.security.require-email-verification:false}") boolean requireEmailVerification) {
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String pan = request.pan().trim().toUpperCase(Locale.ROOT);

        if (request.password().getBytes(StandardCharsets.UTF_8).length > AppConstants.PASSWORD_MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        if (customerProfileRepository.existsByPan(pan)) {
            throw new DuplicateEmailException("A customer profile with this PAN is already registered.");
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                Role.CUSTOMER,
                !requireEmailVerification
        );
        User savedUser = userRepository.save(user);

        CustomerProfile profile = CustomerProfile.builder()
                .user(savedUser)
                .fullName(request.fullName().trim())
                .phone(request.phone().trim())
                .address(request.address().trim())
                .pan(pan)
                .build();
        CustomerProfile savedProfile = customerProfileRepository.save(profile);
        savedUser.setCustomerProfile(savedProfile);

        auditService.log(email, "CUSTOMER_REGISTERED", "User",
                String.valueOf(savedUser.getId()), "Registered new customer account");

        return savedUser;
    }
}
