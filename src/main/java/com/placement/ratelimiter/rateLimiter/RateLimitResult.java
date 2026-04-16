package com.placement.ratelimiter.rateLimiter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private long remaining;
    private int limit;
    private long retryAfterSeconds; 
}
