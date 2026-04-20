package com.placement.ratelimiter.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI/Swagger Configuration.
 * Enhances the generated /swagger-ui/index.html with professional metadata
 * for portfolio presentation.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Distributed Rate Limiter API",
                description = "High-performance, configurable Rate Limiter using Spring Boot, Redis (Lua Scripts), and AOP. " +
                              "Demonstrates Token Bucket & Sliding Window algorithms with fail-open resiliency.",
                version = "v1.0",
                license = @License(
                        name = "MIT License"
                )
        ),
        servers = {
                @Server(
                        description = "Local Development Server",
                        url = "http://localhost:8080"
                )
        }
)
public class OpenApiConfig {
}
