package com.sprint.bank.auth.controller;

import com.sprint.bank.auth.dto.ChangePasswordRequest;
import com.sprint.bank.auth.dto.ForgotPasswordRequest;
import com.sprint.bank.auth.dto.ResetPasswordRequest;
import com.sprint.bank.auth.service.PasswordResetService;
import com.sprint.bank.auth.service.PasswordService;
import com.sprint.bank.common.dto.ApiResponse;
import com.sprint.bank.security.CookieUtil;
import com.sprint.bank.security.principal.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Password Management", description = "Forgot, reset, and authenticated change-password endpoints")
public class PasswordController {

    private final PasswordResetService passwordResetService;
    private final PasswordService passwordService;
    private final CookieUtil cookieUtil;

    public PasswordController(
            PasswordResetService passwordResetService,
            PasswordService passwordService,
            CookieUtil cookieUtil) {
        this.passwordResetService = passwordResetService;
        this.passwordService = passwordService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a single-use password reset link without disclosing account existence")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.message("If an account matches that email, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reset password using a single-use token and revoke active sessions")
    public void resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletResponse response) {
        passwordResetService.reset(request.token(), request.newPassword());
        cookieUtil.clearAuthCookies(response);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change password for the current authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public void changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response) {
        passwordService.change(user.getId(), request.currentPassword(), request.newPassword());
        cookieUtil.clearAuthCookies(response);
    }
}
