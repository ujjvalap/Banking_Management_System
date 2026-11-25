package com.sprint.bank.auth.controller;

import com.sprint.bank.auth.dto.AuthResponse;
import com.sprint.bank.auth.dto.LoginRequest;
import com.sprint.bank.auth.dto.RegisterRequest;
import com.sprint.bank.auth.service.AuthService;
import com.sprint.bank.common.dto.ApiResponse;
import com.sprint.bank.security.CookieUtil;
import com.sprint.bank.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login, and current authenticated user endpoints")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    public AuthController(AuthService authService, CookieUtil cookieUtil) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new customer account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.register(request);
        if (response.accessToken() != null) {
            cookieUtil.addAuthCookies(httpResponse, response.accessToken(), response.refreshToken());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Customer registered successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT access and HttpOnly refresh cookies")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request);
        cookieUtil.addAuthCookies(httpResponse, response.accessToken(), response.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> currentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserResponse user = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Current user fetched", user));
    }
}
