package com.placement.ratelimiter.controller;

import com.placement.ratelimiter.dto.RateLimiterStatsResponse;
import com.placement.ratelimiter.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes real-time rate limiter metrics via REST API.
 * Delegates to MetricsService for data (proper SRP separation).
 * Custom counters are also auto-scraped by Prometheus at /actuator/prometheus.
 */
@RestController
@RequestMapping("/api/rate-limiter")
@RequiredArgsConstructor
@Tag(name = "Telemetry & Metrics", description = "Monitor API rate limiting health and block rates")
public class MetricsController {

    private final MetricsService metricsService;

    @Operation(summary = "Get Live Stats", description = "Fetch real-time block/allow ratios and total request counts tracking the health of the rate limiter.")
    @GetMapping("/stats")
    public ResponseEntity<RateLimiterStatsResponse> getLiveStats() {
        double total = metricsService.getTotalRequests();
        double blocked = metricsService.getBlockedRequests();
        double allowed = total - blocked;
        String blockRate = total > 0 
                ? String.format("%.2f%%", (blocked / total) * 100) 
                : "0.00%";

        return ResponseEntity.ok(new RateLimiterStatsResponse(
            total,
            blocked,
            allowed,
            blockRate,
            "/actuator/prometheus"
        ));
    }
}
