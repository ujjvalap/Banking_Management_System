package com.sprint.bank.transaction.controller;

import com.sprint.bank.common.dto.ApiResponse;
import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.dto.TransferRequest;
import com.sprint.bank.transaction.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Transfers", description = "Atomic, concurrency-safe fund transfers between accounts")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransferService transferService;

    public TransactionController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(summary = "Transfer funds between two active bank accounts")
    public ResponseEntity<ApiResponse<TransactionResponse>> transferMoney(
            @Valid @RequestBody TransferRequest request) {
        TransactionResponse result = transferService.transfer(request);
        return ResponseEntity.ok(ApiResponse.ok("Transfer completed successfully", result));
    }
}
