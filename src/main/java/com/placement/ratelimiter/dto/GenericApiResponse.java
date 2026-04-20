package com.placement.ratelimiter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Generic application response wrapper")
public class GenericApiResponse {
    
    @Schema(description = "Contextual message detailing the result of the operation", example = "Success! You hit the /api/test endpoint.")
    private String message;
    
    @Schema(description = "Optional HTTP status classification", nullable = true, example = "200 OK")
    private String status;
}
