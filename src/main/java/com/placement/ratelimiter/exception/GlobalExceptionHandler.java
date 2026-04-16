package com.placement.ratelimiter.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for consistent JSON error responses.
 * 
 * Handler priority (Spring resolves most-specific first):
 * 1. RateLimitException → 429
 * 2. ResponseStatusException → preserves its original status code
 * 3. NoResourceFoundException → 404 (suppresses noisy favicon.ico stack traces)
 * 4. Exception → 500 (catch-all fallback)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(RateLimitException ex, HttpServletRequest request) {
        Map<String, Object> response = buildErrorBody(
                HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", ex.getMessage(), request.getRequestURI());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        Map<String, Object> response = buildErrorBody(
                status, HttpStatus.valueOf(status).getReasonPhrase(), ex.getReason(), request.getRequestURI());

        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    /**
     * Handles 404 for missing static resources (e.g., /favicon.ico).
     * Without this, NoResourceFoundException floods the logs with full stack traces
     * every time a browser auto-requests favicon.ico. Log at DEBUG, not ERROR.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("Resource not found: {}", request.getRequestURI());
        Map<String, Object> response = buildErrorBody(
                HttpStatus.NOT_FOUND.value(), "Not Found", "No resource found at " + request.getRequestURI(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        Map<String, Object> response = buildErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", ex.getMessage(), request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private Map<String, Object> buildErrorBody(int status, String error, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);
        return body;
    }
}
