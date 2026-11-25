package com.sprint.bank;

import com.sprint.bank.security.RequestRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestRateLimitFilterTests {

    @Test
    void appliesAuthenticationRateLimitPerRemoteAddress() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(10, 1, 10, 10);
        AtomicInteger passedRequests = new AtomicInteger();

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/auth/login");
        first.setRemoteAddr("198.51.100.10");
        filter.doFilter(first, new MockHttpServletResponse(), (req, res) -> passedRequests.incrementAndGet());

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/auth/login");
        second.setRemoteAddr("198.51.100.10");
        MockHttpServletResponse secondRes = new MockHttpServletResponse();
        filter.doFilter(second, secondRes, (req, res) -> passedRequests.incrementAndGet());

        assertEquals(1, passedRequests.get());
        assertEquals(429, secondRes.getStatus());
        assertNotNull(secondRes.getHeader("Retry-After"));
        assertEquals("0", secondRes.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void appliesTransactionRateLimitToDepositWithdrawAndTransfer() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(20, 20, 2, 10);
        AtomicInteger passedRequests = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/transfers");
            req.setRemoteAddr("198.51.100.25");
            filter.doFilter(req, new MockHttpServletResponse(), (req2, res2) -> passedRequests.incrementAndGet());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/accounts/1/deposit");
        blockedReq.setRemoteAddr("198.51.100.25");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, (req2, res2) -> passedRequests.incrementAndGet());

        assertEquals(2, passedRequests.get());
        assertEquals(429, blockedRes.getStatus());
        assertNotNull(blockedRes.getHeader("Retry-After"));
    }
}
