package com.sprint.bank.customer.service.impl;

import com.sprint.bank.account.repository.AccountRepository;
import com.sprint.bank.audit.service.AuditService;
import com.sprint.bank.customer.dto.CustomerProfileRequest;
import com.sprint.bank.customer.dto.CustomerResponse;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.customer.repository.CustomerProfileRepository;
import com.sprint.bank.customer.service.CustomerService;
import com.sprint.bank.exception.ResourceNotFoundException;
import com.sprint.bank.mapper.CustomerMapper;
import com.sprint.bank.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerProfileRepository customerProfileRepository;
    private final AccountRepository accountRepository;
    private final CustomerMapper customerMapper;
    private final AuditService auditService;

    public CustomerServiceImpl(
            CustomerProfileRepository customerProfileRepository,
            AccountRepository accountRepository,
            CustomerMapper customerMapper,
            AuditService auditService) {
        this.customerProfileRepository = customerProfileRepository;
        this.accountRepository = accountRepository;
        this.customerMapper = customerMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getMyProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found."));
        int accountCount = (int) accountRepository.countByCustomerId(profile.getId());
        return customerMapper.toResponse(profile, accountCount);
    }

    @Override
    @Transactional
    public CustomerResponse updateMyProfile(CustomerProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found."));

        profile.setFullName(request.fullName().trim());
        profile.setPhone(request.phone().trim());
        profile.setAddress(request.address().trim());

        CustomerProfile saved = customerProfileRepository.save(profile);
        auditService.log(SecurityUtils.getCurrentUserEmail(), "CUSTOMER_PROFILE_UPDATED", "CustomerProfile",
                String.valueOf(saved.getId()), "Customer updated profile contact details");

        int accountCount = (int) accountRepository.countByCustomerId(saved.getId());
        return customerMapper.toResponse(saved, accountCount);
    }
}
