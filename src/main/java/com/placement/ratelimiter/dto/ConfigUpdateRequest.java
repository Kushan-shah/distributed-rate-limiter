package com.placement.ratelimiter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to dynamically update a rate limit rule")
public class ConfigUpdateRequest {
    
    @Schema(description = "The target API endpoint path", example = "/api/login", required = true)
    private String endpoint;
    
    @Schema(description = "Maximum allowed requests within the time window", example = "5", required = true)
    private int limit;
    
    @Schema(description = "Time window duration in seconds", example = "60", required = true)
    private int windowSeconds;
}
