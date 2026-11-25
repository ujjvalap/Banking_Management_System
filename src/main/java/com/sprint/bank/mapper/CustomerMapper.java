package com.sprint.bank.mapper;

import com.sprint.bank.customer.dto.CustomerResponse;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(CustomerProfile profile, int accountCount) {
        User user = profile.getUser();
        return new CustomerResponse(
                profile.getId(),
                user.getId(),
                profile.getFullName(),
                user.getEmail(),
                profile.getPhone(),
                profile.getAddress(),
                profile.getPan(),
                user.isEnabled(),
                accountCount,
                user.getCreatedAt()
        );
    }
}
