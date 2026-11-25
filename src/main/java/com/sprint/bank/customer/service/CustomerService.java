package com.sprint.bank.customer.service;

import com.sprint.bank.customer.dto.CustomerProfileRequest;
import com.sprint.bank.customer.dto.CustomerResponse;

public interface CustomerService {

    CustomerResponse getMyProfile();

    CustomerResponse updateMyProfile(CustomerProfileRequest request);
}
