package com.placement.ratelimiter.service;

import com.placement.ratelimiter.config.RateLimiterProperties;
import com.placement.ratelimiter.rateLimiter.RateLimitResult;
import com.placement.ratelimiter.rateLimiter.RateLimiterStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Core orchestration service for rate limiting.
 * 
 * Responsibilities:
 * 1. Resolve which Rule applies (endpoint > role > default)
 * 2. Construct the Redis key using identity hierarchy
 * 3. Delegate to the active Strategy for the allow/deny decision
 *
 * Rule Precedence (intentional design):
 * Endpoint-specific rules ALWAYS take priority over role rules.
 * This means an ADMIN hitting /api/login still gets the strict /api/login limit (3/min),
 * not the relaxed ADMIN limit (100/min). This is intentional: login brute-force
 * protection should apply regardless of claimed role (roles can be spoofed via headers).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RateLimiterProperties properties;
    private final Map<String, RateLimiterStrategy> strategies;

    public RateLimitResult checkRateLimit(String requestPath, String userId, String role, String ip) {
        if (!properties.isEnabled()) {
            return new RateLimitResult(true, 1, 1, 0);
        }

        // 1. Determine Limit & Window Rules (Endpoint > Role > Default)
        RateLimiterProperties.Rule rule = getRule(requestPath, role);

        // 2. Build Key (Identity hierarchy: User > Role > IP > Anonymous)
        String key = buildKey(requestPath, userId, role, ip);

        log.debug("Rate check | key={} | limit={} | window={}s", key, rule.getLimit(), rule.getWindowSeconds());

        // 3. Apply active strategy
        RateLimiterStrategy strategy = strategies.get(properties.getActiveStrategy());
        if (strategy == null) {
            log.error("Strategy {} not found! Falling back to TOKEN_BUCKET", properties.getActiveStrategy());
            strategy = strategies.get("TOKEN_BUCKET");
        }

        return strategy.isAllowed(key, rule.getLimit(), rule.getWindowSeconds());
    }

    /**
     * Rule resolution priority:
     * 1. Endpoint-specific (e.g., /api/login → 3 req/min)
     *    → Security: applies regardless of role (prevents brute-force)
     * 2. Role-based (e.g., ROLE_ADMIN → 100 req/min)
     *    → Only applies if no endpoint-specific rule exists
     * 3. Global default (e.g., 10 req/min for all unauthenticated traffic)
     */
    private RateLimiterProperties.Rule getRule(String requestPath, String role) {
        // Priority 1: Endpoint-specific rules (security-critical endpoints like /login)
        if (requestPath != null && properties.getEndpoints().containsKey(requestPath)) {
            return properties.getEndpoints().get(requestPath);
        }

        // Priority 2: Role-based rules
        if (role != null && !role.isEmpty() && properties.getRoles().containsKey(role)) {
            return properties.getRoles().get(role);
        }

        // Priority 3: Global default
        RateLimiterProperties.Rule defaultRule = new RateLimiterProperties.Rule();
        defaultRule.setLimit(properties.getDefaultLimit());
        defaultRule.setWindowSeconds(properties.getDefaultWindowSeconds());
        return defaultRule;
    }

    /**
     * Key construction hierarchy:
     * Most specific identity wins → prevents shared-bucket pollution.
     * 
     * Examples:
     *   rate_limit:user:alice:/api/orders  (authenticated user)
     *   rate_limit:role:ROLE_ADMIN:/api/admin  (role-only, no user id)
     *   rate_limit:ip:192.168.1.1:/api/login  (unauthenticated, IP fallback)
     *   rate_limit:global:anon:/api/test  (absolute fallback)
     */
    private String buildKey(String requestPath, String userId, String role, String ip) {
        StringBuilder key = new StringBuilder("rate_limit");

        if (userId != null && !userId.isEmpty()) {
            key.append(":user:").append(userId);
        } else if (role != null && !role.isEmpty()) {
            key.append(":role:").append(role);
        } else if (ip != null && !ip.isEmpty()) {
            key.append(":ip:").append(ip);
        } else {
            key.append(":global:anon");
        }

        if (requestPath != null && !requestPath.isEmpty()) {
            key.append(":").append(requestPath);
        }

        return key.toString();
    }
    
    /**
     * Admin function to dynamically update rules at runtime.
     * Thread-safe: RateLimiterProperties uses ConcurrentHashMap.
     */
    public void updateEndpointRule(String endpoint, int limit, int windowSeconds) {
        RateLimiterProperties.Rule newRule = new RateLimiterProperties.Rule();
        newRule.setLimit(limit);
        newRule.setWindowSeconds(windowSeconds);
        properties.getEndpoints().put(endpoint, newRule);
        log.info("Dynamically updated rate limit for {}: {} req/{}s", endpoint, limit, windowSeconds);
    }
}
