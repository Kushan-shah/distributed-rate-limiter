package com.placement.ratelimiter.controller;

import com.placement.ratelimiter.dto.ConfigUpdateRequest;
import com.placement.ratelimiter.dto.ConfigUpdateResponse;
import com.placement.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin API for dynamic rate limit configuration.
 * Allows ops/SRE teams to adjust limits at runtime without redeployment.
 */
@RestController
@RequestMapping("/api/rate-limiter")
@RequiredArgsConstructor
@Tag(name = "Admin Configurations", description = "Dynamic rate-limit configurations without downtime")
public class AdminController {

    private final RateLimiterService rateLimiterService;

    @Operation(summary = "Update Endpoint Limit", description = "Dynamically update the rate limit rule for a specific endpoint using ConcurrentHashMap.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rule successfully updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload or negative inputs")
    })
    @PostMapping("/config")
    public ResponseEntity<ConfigUpdateResponse> updateLimit(
            @RequestBody ConfigUpdateRequest request) {

        // Input validation: prevents division-by-zero in Lua (capacity / refill_time)
        // and nonsensical configurations
        if (request.getLimit() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Limit must be a positive integer. Received: " + request.getLimit());
        }
        if (request.getWindowSeconds() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Window seconds must be a positive integer. Received: " + request.getWindowSeconds());
        }
        if (request.getEndpoint() == null || request.getEndpoint().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Endpoint must not be empty.");
        }

        rateLimiterService.updateEndpointRule(request.getEndpoint(), request.getLimit(), request.getWindowSeconds());

        return ResponseEntity.ok(new ConfigUpdateResponse(
            "Rate limit dynamically updated",
            request.getEndpoint(),
            request.getLimit(),
            request.getWindowSeconds()
        ));
    }
}
