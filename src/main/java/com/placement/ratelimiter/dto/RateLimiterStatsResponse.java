package com.placement.ratelimiter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Real-time telemetry and metrics for the Rate Limiter subsystem")
public class RateLimiterStatsResponse {
    
    @Schema(description = "Total number of requests processed by the application", example = "15420")
    private double totalRequests;
    
    @Schema(description = "Total number of requests actively discarded by the rate limiter (HTTP 429)", example = "350")
    private double blockedRequests;
    
    @Schema(description = "Total number of requests permitted to reach the backend services", example = "15070")
    private double allowedRequests;
    
    @Schema(description = "Percentage of traffic being blocked automatically", example = "2.27%")
    private String blockRate;
    
    @Schema(description = "URI to access raw Prometheus format metrics", example = "/actuator/prometheus")
    private String prometheusEndpoint;
}
