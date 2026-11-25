package com.sprint.bank.customer.controller;

import com.sprint.bank.common.dto.ApiResponse;
import com.sprint.bank.customer.dto.CustomerProfileRequest;
import com.sprint.bank.customer.dto.CustomerResponse;
import com.sprint.bank.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customers", description = "Authenticated customer profile management")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    @Operation(summary = "View authenticated customer profile")
    public ResponseEntity<ApiResponse<CustomerResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.ok("Customer profile retrieved", customerService.getMyProfile()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update authenticated customer profile")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateMyProfile(
            @Valid @RequestBody CustomerProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Customer profile updated", customerService.updateMyProfile(request)));
    }
}
