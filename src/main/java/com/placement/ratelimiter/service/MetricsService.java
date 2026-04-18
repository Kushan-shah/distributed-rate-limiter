package com.placement.ratelimiter.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/**
 * Centralized metrics recording service.
 * Separated from the controller to follow Single Responsibility Principle
 * and avoid a circular dependency (Filter → Controller is an anti-pattern).
 */
@Service
public class MetricsService {

    private final Counter totalRequestsCounter;
    private final Counter blockedRequestsCounter;

    public MetricsService(MeterRegistry meterRegistry) {
        this.totalRequestsCounter = Counter.builder("rate_limiter_requests_total")
                .description("Total number of rate-limited requests processed")
                .register(meterRegistry);
        this.blockedRequestsCounter = Counter.builder("rate_limiter_requests_blocked")
                .description("Total number of requests blocked by rate limiter")
                .register(meterRegistry);
    }

    public void recordRequest(boolean blocked) {
        totalRequestsCounter.increment();
        if (blocked) {
            blockedRequestsCounter.increment();
        }
    }

    public double getTotalRequests() {
        return totalRequestsCounter.count();
    }

    public double getBlockedRequests() {
        return blockedRequestsCounter.count();
    }
}
