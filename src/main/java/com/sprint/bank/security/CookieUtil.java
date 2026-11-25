package com.sprint.bank.security;

import com.sprint.bank.common.constants.AppConstants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    private final boolean cookieSecure;
    private final String sameSite;
    private final long accessExpirationMillis;
    private final long refreshExpirationMillis;

    public CookieUtil(
            @Value("${app.security.cookie-secure:false}") boolean cookieSecure,
            @Value("${app.security.cookie-same-site:Lax}") String sameSite,
            @Value("${app.jwt.access-expiration:900000}") long accessExpirationMillis,
            @Value("${app.jwt.refresh-expiration:604800000}") long refreshExpirationMillis) {
        this.cookieSecure = cookieSecure;
        this.sameSite = sameSite;
        this.accessExpirationMillis = accessExpirationMillis;
        this.refreshExpirationMillis = refreshExpirationMillis;
    }

    public void addAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            ResponseCookie accessCookie = ResponseCookie.from(AppConstants.ACCESS_TOKEN_COOKIE, accessToken)
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite(sameSite)
                    .path("/")
                    .maxAge(Duration.ofMillis(accessExpirationMillis))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            ResponseCookie refreshCookie = ResponseCookie.from(AppConstants.REFRESH_TOKEN_COOKIE, refreshToken)
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite(sameSite)
                    .path("/api/auth")
                    .maxAge(Duration.ofMillis(refreshExpirationMillis))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }
    }

    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from(AppConstants.ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from(AppConstants.REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue().trim();
            }
        }
        return null;
    }
}
