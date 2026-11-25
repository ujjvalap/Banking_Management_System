package com.sprint.bank.auth.controller;

import com.sprint.bank.auth.dto.RefreshTokenRequest;
import com.sprint.bank.auth.dto.TokenResponse;
import com.sprint.bank.auth.service.RefreshTokenService;
import com.sprint.bank.common.constants.AppConstants;
import com.sprint.bank.common.dto.ApiResponse;
import com.sprint.bank.security.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class RefreshTokenController {

    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    public RefreshTokenController(RefreshTokenService refreshTokenService, CookieUtil cookieUtil) {
        this.refreshTokenService = refreshTokenService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token from body or HttpOnly cookie and issue new tokens")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String rawToken = (request != null && request.refreshToken() != null && !request.refreshToken().isBlank())
                ? request.refreshToken()
                : cookieUtil.extractCookie(httpRequest, AppConstants.REFRESH_TOKEN_COOKIE);

        TokenResponse tokens = refreshTokenService.rotate(rawToken);
        cookieUtil.addAuthCookies(httpResponse, tokens.accessToken(), tokens.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed successfully", tokens));
    }
}
