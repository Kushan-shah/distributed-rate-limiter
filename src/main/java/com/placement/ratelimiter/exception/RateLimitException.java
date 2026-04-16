package com.placement.ratelimiter.exception;

import lombok.Getter;

@Getter
public class RateLimitException extends RuntimeException {
    
    private final int retryAfterSeconds;
    
    public RateLimitException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
