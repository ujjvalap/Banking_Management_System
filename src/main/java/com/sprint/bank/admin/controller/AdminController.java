package com.sprint.bank.admin.controller;

import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.admin.dto.AccountStatusRequest;
import com.sprint.bank.admin.service.AdminService;
import com.sprint.bank.common.dto.ApiResponse;
import com.sprint.bank.common.dto.PageResponse;
import com.sprint.bank.customer.dto.CustomerResponse;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.enums.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "Admin oversight for customers, accounts, status controls, and transactions")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get system-wide summary metrics")
    public ResponseEntity<ApiResponse<AdminService.AdminSummary>> getSummary() {
        return ResponseEntity.ok(ApiResponse.ok("Admin summary retrieved", adminService.getSummary()));
    }

    @GetMapping("/customers")
    @Operation(summary = "List all registered customers")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getCustomers() {
        return ResponseEntity.ok(ApiResponse.ok("Customers retrieved", adminService.getAllCustomers()));
    }

    @GetMapping("/accounts")
    @Operation(summary = "List all bank accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts() {
        return ResponseEntity.ok(ApiResponse.ok("Accounts retrieved", adminService.getAllAccounts()));
    }

    @GetMapping("/accounts/{id}")
    @Operation(summary = "Inspect details of a specific account")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Account details retrieved", adminService.getAccountById(id)));
    }

    @PutMapping("/accounts/{id}/status")
    @Operation(summary = "Freeze, activate, or close a bank account")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccountStatus(
            @PathVariable Long id,
            @Valid @RequestBody AccountStatusRequest request) {
        AccountResponse updated = adminService.updateAccountStatus(id, request.status());
        return ResponseEntity.ok(ApiResponse.ok("Account status updated to " + updated.getStatus(), updated));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Search and filter system-wide transactions by date range and type")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<TransactionResponse> transactions =
                adminService.getTransactions(startDate, endDate, type, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Transactions retrieved", transactions));
    }
}
