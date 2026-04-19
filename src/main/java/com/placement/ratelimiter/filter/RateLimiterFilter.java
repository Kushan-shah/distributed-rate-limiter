package com.placement.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.placement.ratelimiter.rateLimiter.RateLimitResult;
import com.placement.ratelimiter.service.MetricsService;
import com.placement.ratelimiter.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.placement.ratelimiter.config.CacheConfig;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global HTTP filter that intercepts all incoming API requests.
 * 
 * Execution order:
 * 1. CorrelationIdFilter (HIGHEST_PRECEDENCE) → assigns X-Request-Id
 * 2. RateLimiterFilter (HIGHEST_PRECEDENCE + 1) → this filter
 * 3. Controller method (optionally guarded by @RateLimit AOP)
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimiterFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || 
               path.startsWith("/v3/api-docs") || 
               path.startsWith("/actuator") ||
               path.startsWith("/api/rate-limiter") ||
               path.equals("/favicon.ico") ||
               path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-Role");
        String ip = getClientIP(request);

        // --- L1 SHORT-CIRCUIT CACHE CHECK ---
        Cache blockedCache = cacheManager.getCache(CacheConfig.BLOCKED_IP_CACHE);
        if (blockedCache != null && blockedCache.get(ip) != null) {
            // Client is cached as blocked. Immediately reject without Redis overhead.
            log.warn("L1 Cache Short-Circuit Triggered | path={} | ip={}", path, ip);
            metricsService.recordRequest(true);
            
            // Assume the retry-after is 5 seconds matching the TTL of the block cache
            response.setHeader("Retry-After", "5");
            sendErrorResponse(response, path, 5L);
            return;
        }

        RateLimitResult result = rateLimiterService.checkRateLimit(path, userId, role, ip);

        // Always inject RFC-compliant rate limit headers (even on success)
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, result.getRemaining())));
        
        long resetTime = (System.currentTimeMillis() / 1000) + result.getRetryAfterSeconds();
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));

        if (!result.isAllowed()) {
            log.warn("L2 Redis limit exceeded | path={} | ip={} | userId={} | role={}", path, ip, userId, role);
            
            // Add to L1 Block Cache so subsequent immediate requests are short-circuited
            if (blockedCache != null) {
                blockedCache.put(ip, true); // Storing a dummy boolean, the presence is what matters
            }

            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            
            metricsService.recordRequest(true);
            sendErrorResponse(response, path, result.getRetryAfterSeconds());
            return;
        }

        metricsService.recordRequest(false);
        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        // Check standard proxy headers in priority order
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void sendErrorResponse(HttpServletResponse response, String path, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");

        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now().toString());
        errorDetails.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        errorDetails.put("error", "Too Many Requests");
        errorDetails.put("message", "Rate limit exceeded. Try again in " + retryAfter + " seconds.");
        errorDetails.put("path", path);

        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }
}
