package com.sprint.bank.auth.controller;

import com.sprint.bank.auth.dto.ForgotPasswordRequest;
import com.sprint.bank.auth.dto.VerifyEmailRequest;
import com.sprint.bank.auth.service.EmailVerificationService;
import com.sprint.bank.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Email Verification", description = "Verify email addresses via single-use tokens")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify customer email address using query token")
    public ResponseEntity<ApiResponse<Void>> verifyEmailGet(@RequestParam String token) {
        emailVerificationService.verify(token);
        return ResponseEntity.ok(ApiResponse.message("Email verified successfully."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify customer email address using JSON token payload")
    public ResponseEntity<ApiResponse<Void>> verifyEmailPost(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(ApiResponse.message("Email verified successfully."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        emailVerificationService.resendVerification(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.message("If your account requires verification, a new link has been sent."));
    }
}
