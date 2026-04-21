package com.placement.ratelimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the rate limiter.
 * 
 * PREREQUISITE: Redis must be running on localhost:6379.
 * Start via: docker-compose up -d
 * 
 * These tests validate end-to-end behavior through the full Spring filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimiterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should block 4th request when endpoint limit is 3/min")
    void testRateLimitEnforcement() throws Exception {
        // application.yml: /api/login → 3 req / 60s
        // Use a unique IP per test run to avoid cross-test pollution
        String ip = "10.0.0." + (int)(Math.random() * 255);
        
        // Allowed Requests 1-3
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(get("/api/login").header("X-Forwarded-For", ip))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-RateLimit-Remaining"));
        }

        // Blocked Request 4 (Rate limit kicks in)
        mockMvc.perform(get("/api/login").header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    @DisplayName("Should return rate limit headers on every response")
    void testRateLimitHeadersPresent() throws Exception {
        String ip = "10.0.1." + (int)(Math.random() * 255);

        mockMvc.perform(get("/api/test").header("X-Forwarded-For", ip))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("Should apply role-based limits when X-Role header is present")
    void testRoleBasedLimits() throws Exception {
        String ip = "10.0.2." + (int)(Math.random() * 255);

        // ROLE_USER has limit 20/min — first request should pass
        mockMvc.perform(get("/api/test")
                        .header("X-Forwarded-For", ip)
                        .header("X-Role", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "20"));
    }
}
