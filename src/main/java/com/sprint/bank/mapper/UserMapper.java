package com.sprint.bank.mapper;

import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.user.dto.UserResponse;
import com.sprint.bank.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        CustomerProfile profile = user.getCustomerProfile();
        String fullName = profile != null ? profile.getFullName() : "Bank Administrator";
        String phone = profile != null ? profile.getPhone() : null;
        String address = profile != null ? profile.getAddress() : null;
        String pan = profile != null ? profile.getPan() : null;

        return new UserResponse(
                user.getId(),
                fullName,
                user.getEmail(),
                phone,
                address,
                pan,
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
