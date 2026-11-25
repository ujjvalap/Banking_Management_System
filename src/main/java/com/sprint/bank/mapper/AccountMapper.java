package com.sprint.bank.mapper;

import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.account.entity.Account;
import com.sprint.bank.customer.entity.CustomerProfile;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        CustomerProfile customer = account.getCustomer();
        String customerName = customer != null ? customer.getFullName() : null;
        String customerEmail = (customer != null && customer.getUser() != null)
                ? customer.getUser().getEmail()
                : null;
        Long customerId = customer != null ? customer.getId() : null;

        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .type(account.getType())
                .balance(account.getBalance())
                .status(account.getStatus())
                .customerId(customerId)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .createdAt(account.getCreatedAt())
                .build();
    }
}
