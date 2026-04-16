package com.placement.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Externalized rate limiter configuration from application.yml.
 * Uses ConcurrentHashMap for thread-safe dynamic updates at runtime.
 */
@Data
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {
    private boolean enabled = true;
    private String activeStrategy = "TOKEN_BUCKET";
    private int defaultLimit = 10;
    private int defaultWindowSeconds = 60;

    // THREAD SAFETY: ConcurrentHashMap is essential here.
    // The AdminController writes to this map while the RateLimiterFilter reads from it
    // concurrently on different threads. A plain HashMap would throw
    // ConcurrentModificationException or silently corrupt data under load.
    private Map<String, Rule> endpoints = new ConcurrentHashMap<>();
    private Map<String, Rule> roles = new ConcurrentHashMap<>();

    @Data
    public static class Rule {
        private int limit;
        private int windowSeconds;
    }
}
