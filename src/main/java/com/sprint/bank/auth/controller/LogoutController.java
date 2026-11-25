package com.sprint.bank.auth.controller;

import com.sprint.bank.auth.service.LogoutService;
import com.sprint.bank.security.CookieUtil;
import com.sprint.bank.security.principal.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class LogoutController {

    private final LogoutService logoutService;
    private final CookieUtil cookieUtil;

    public LogoutController(LogoutService logoutService, CookieUtil cookieUtil) {
        this.logoutService = logoutService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke sessions and clear authentication cookies", security = @SecurityRequirement(name = "bearerAuth"))
    public void logout(
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletResponse response) {
        logoutService.logout(user.getId());
        cookieUtil.clearAuthCookies(response);
    }
}
