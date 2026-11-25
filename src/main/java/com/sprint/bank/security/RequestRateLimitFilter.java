package com.sprint.bank.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Object cacheLock = new Object();
    private final int apiRequestsPerMinute;
    private final int authRequestsPerMinute;
    private final int transactionRequestsPerMinute;
    private final int maximumClients;

    public RequestRateLimitFilter(
            int apiRequestsPerMinute,
            int authRequestsPerMinute,
            int maximumClients) {
        this(apiRequestsPerMinute, authRequestsPerMinute, 30, maximumClients);
    }

    @Autowired
    public RequestRateLimitFilter(
            @Value("${app.security.rate-limit.api-per-minute:120}") int apiRequestsPerMinute,
            @Value("${app.security.rate-limit.auth-per-minute:20}") int authRequestsPerMinute,
            @Value("${app.security.rate-limit.transaction-per-minute:30}") int transactionRequestsPerMinute,
            @Value("${app.security.rate-limit.max-clients:10000}") int maximumClients) {
        if (apiRequestsPerMinute < 1
                || authRequestsPerMinute < 1
                || transactionRequestsPerMinute < 1
                || maximumClients < 1) {
            throw new IllegalArgumentException("Rate limits and maximum clients must be positive.");
        }
        this.apiRequestsPerMinute = apiRequestsPerMinute;
        this.authRequestsPerMinute = authRequestsPerMinute;
        this.transactionRequestsPerMinute = transactionRequestsPerMinute;
        this.maximumClients = maximumClients;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean apiRequest = path.startsWith("/api/");
        boolean authRequest = isAuthenticationRequest(method, path);
        boolean transactionRequest = isTransactionRequest(method, path);

        if (!apiRequest && !authRequest && !transactionRequest) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        long now = System.currentTimeMillis();

        boolean allowed = true;
        if (apiRequest) {
            allowed = allow(clientKey + ":api", apiRequestsPerMinute, now, response, true);
        }
        if (allowed && authRequest) {
            allowed = allow(clientKey + ":auth", authRequestsPerMinute, now, response, !apiRequest);
        }
        if (allowed && transactionRequest) {
            allowed = allow(clientKey + ":tx", transactionRequestsPerMinute, now, response, true);
        }

        if (!allowed) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please wait before retrying.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean allow(
            String key,
            int limit,
            long now,
            HttpServletResponse response,
            boolean writeHeaders) {
        Window window = windows.get(key);
        if (window == null) {
            synchronized (cacheLock) {
                window = windows.get(key);
                if (window == null) {
                    windows.entrySet().removeIf(entry -> {
                        Window candidate = entry.getValue();
                        synchronized (candidate) {
                            return now - candidate.startedAt >= WINDOW_MILLIS;
                        }
                    });
                    if (windows.size() >= (long) maximumClients * 3) {
                        setRetryAfter(response, 60);
                        return false;
                    }
                    window = new Window(now);
                    windows.put(key, window);
                }
            }
        }

        synchronized (window) {
            if (now - window.startedAt >= WINDOW_MILLIS) {
                window.startedAt = now;
                window.requests = 0;
            }
            long resetSeconds = Math.max(1, (WINDOW_MILLIS - (now - window.startedAt) + 999) / 1000);
            if (window.requests >= limit) {
                setRetryAfter(response, resetSeconds);
                response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("X-RateLimit-Reset", Long.toString(resetSeconds));
                return false;
            }
            window.requests++;
            if (writeHeaders) {
                response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
                response.setHeader("X-RateLimit-Remaining", Integer.toString(Math.max(0, limit - window.requests)));
                response.setHeader("X-RateLimit-Reset", Long.toString(resetSeconds));
            }
            return true;
        }
    }

    private void setRetryAfter(HttpServletResponse response, long seconds) {
        response.setHeader("Retry-After", Long.toString(seconds));
        response.setHeader("Cache-Control", "no-store");
    }

    private boolean isAuthenticationRequest(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return path.matches("/api/auth/(login|register|refresh|forgot-password|reset-password|change-password|resend-verification)");
    }

    private boolean isTransactionRequest(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return path.matches("/api/accounts/\\d+/(deposit|withdraw)")
                || path.equals("/api/transfers");
    }

    private static final class Window {
        private long startedAt;
        private int requests;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
