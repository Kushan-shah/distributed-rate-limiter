package com.placement.ratelimiter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response returned after a successful rate limit rule update")
public class ConfigUpdateResponse {
    
    @Schema(description = "Confirmation message", example = "Rate limit dynamically updated")
    private String message;
    
    @Schema(description = "The updated API endpoint path", example = "/api/login")
    private String endpoint;
    
    @Schema(description = "The newly configured limit", example = "5")
    private int newLimit;
    
    @Schema(description = "The newly configured window in seconds", example = "60")
    private int newWindowSeconds;
}
