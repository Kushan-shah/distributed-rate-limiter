package com.placement.ratelimiter.rateLimiter;

public interface RateLimiterStrategy {
    RateLimitResult isAllowed(String key, int limit, int windowInSeconds);
}
