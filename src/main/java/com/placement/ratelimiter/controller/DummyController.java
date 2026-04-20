package com.placement.ratelimiter.controller;

import com.placement.ratelimiter.annotation.RateLimit;
import com.placement.ratelimiter.dto.GenericApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Testing APIs", description = "Mock endpoints to test various rate limit scenarios")
public class DummyController {

    // 1. General Endpoint: Handled by Global Filter rules
    @Operation(summary = "General Test Endpoint", description = "Test the global default rate limit (e.g., 10 req/min).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful hit"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded (Global Default)")
    })
    @GetMapping("/test")
    public ResponseEntity<GenericApiResponse> testEndpoint() {
        return ResponseEntity.ok(new GenericApiResponse(
            "Success! You hit the /api/test endpoint.",
            "200 OK"
        ));
    }

    // 2. Specific Config Endpoint: Handled by application.yml rule for /api/login
    @Operation(summary = "Login Endpoint", description = "Test specific path limits loaded from application.yml (e.g., 3 req/min).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful authentication"),
        @ApiResponse(responseCode = "429", description = "Too many login attempts. Rate limit exceeded.")
    })
    @GetMapping("/login")
    public ResponseEntity<GenericApiResponse> loginEndpoint() {
        return ResponseEntity.ok(new GenericApiResponse(
            "Success! You hit the /api/login endpoint.",
            null
        ));
    }
    
    // 3. Admin Endpoint: Handled by Role-based logic
    @Operation(summary = "Admin Portal", description = "Test higher capacity limits reserved for Admin roles.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Admin dashboard loaded"),
        @ApiResponse(responseCode = "429", description = "Admin limit exceeded")
    })
    @GetMapping("/admin")
    public ResponseEntity<GenericApiResponse> adminEndpoint() {
        return ResponseEntity.ok(new GenericApiResponse(
            "Success! Admin portal accessed.",
            null
        ));
    }

    // 4. AOP Annotation Endpoint: Enforced explicitly via annotation
    @Operation(summary = "AOP Test Endpoint", description = "Test rate limiting enforced specifically via @RateLimit AOP annotations (e.g., 2 req/min).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "429", description = "AOP-level limit exceeded")
    })
    @GetMapping("/aop-test")
    @RateLimit(capacity = 2, windowSeconds = 60)
    public ResponseEntity<GenericApiResponse> aopTestEndpoint() {
        return ResponseEntity.ok(new GenericApiResponse(
            "Success! You hit the strictly annotated AOP endpoint.",
            null
        ));
    }
}
