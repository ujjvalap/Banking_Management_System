package com.sprint.bank.account.controller;

import com.sprint.bank.account.dto.AccountRequest;
import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.account.service.AccountService;
import com.sprint.bank.common.dto.ApiResponse;
import com.sprint.bank.common.dto.PageResponse;
import com.sprint.bank.transaction.dto.DepositRequest;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.dto.WithdrawRequest;
import com.sprint.bank.transaction.enums.TransactionType;
import com.sprint.bank.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Accounts", description = "Customer account opening, balances, deposits, withdrawals, and statements")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public AccountController(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(summary = "Open a new SAVINGS or CURRENT bank account")
    public ResponseEntity<ApiResponse<AccountResponse>> openAccount(
            @Valid @RequestBody AccountRequest request) {
        AccountResponse created = accountService.openAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account opened successfully", created));
    }

    @GetMapping
    @Operation(summary = "List all accounts belonging to the authenticated customer")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts() {
        return ResponseEntity.ok(ApiResponse.ok("Accounts retrieved", accountService.getMyAccounts()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get details of an owned account")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Account retrieved", accountService.getAccount(id)));
    }

    @PostMapping("/{id}/deposit")
    @Operation(summary = "Deposit money into an owned ACTIVE account")
    public ResponseEntity<ApiResponse<AccountResponse>> deposit(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Deposit completed", accountService.deposit(id, request)));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw money from an owned ACTIVE account")
    public ResponseEntity<ApiResponse<AccountResponse>> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Withdrawal completed", accountService.withdraw(id, request)));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get paginated and filtered transaction statement for an owned account")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getTransactions(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<TransactionResponse> statement =
                transactionService.getAccountStatement(id, startDate, endDate, type, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Account statement retrieved", statement));
    }

    @GetMapping("/transactions/recent")
    @Operation(summary = "Get recent transactions across all accounts of the authenticated customer")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getRecentTransactions() {
        return ResponseEntity.ok(ApiResponse.ok("Recent transactions retrieved", transactionService.getMyRecentTransactions()));
    }
}
