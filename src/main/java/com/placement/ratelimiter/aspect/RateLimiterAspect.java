package com.placement.ratelimiter.aspect;

import com.placement.ratelimiter.annotation.RateLimit;
import com.placement.ratelimiter.config.RateLimiterProperties;
import com.placement.ratelimiter.exception.RateLimitException;
import com.placement.ratelimiter.rateLimiter.RateLimitResult;
import com.placement.ratelimiter.rateLimiter.RateLimiterStrategy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * AOP Aspect that enforces rate limits on methods annotated with @RateLimit.
 * 
 * This provides a second, granular layer of defense on top of the global filter.
 * The global filter applies broad per-IP/per-user limits, while this annotation
 * allows specific methods to define their own stricter thresholds.
 *
 * Design Note: The AOP key uses a different prefix ("rate_limit:aop:") than the
 * global filter key, so they operate on independent Redis buckets intentionally.
 * This is "layered defense" - even if the global limit allows a request,
 * the AOP annotation can still block it for extra-sensitive endpoints.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final Map<String, RateLimiterStrategy> strategies;
    private final RateLimiterProperties properties;

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            
            // Use X-User-Id if available, otherwise fall back to IP
            String identifier = request.getHeader("X-User-Id");
            if (identifier == null || identifier.isEmpty()) {
                identifier = request.getRemoteAddr();
            }
            
            String methodName = joinPoint.getSignature().toShortString();
            String key = "rate_limit:aop:" + methodName + ":" + identifier;

            // Use the globally configured active strategy (not hardcoded)
            String strategyName = properties.getActiveStrategy();
            RateLimiterStrategy strategy = strategies.get(strategyName);
            if (strategy == null) {
                log.error("AOP: Strategy {} not found, falling back to TOKEN_BUCKET", strategyName);
                strategy = strategies.get("TOKEN_BUCKET");
            }
            
            if (strategy != null) {
                RateLimitResult result = strategy.isAllowed(key, rateLimit.capacity(), rateLimit.windowSeconds());
                if (!result.isAllowed()) {
                    log.warn("AOP @RateLimit exceeded | method={} | identifier={}", methodName, identifier);
                    throw new RateLimitException(
                        "Rate limit exceeded for " + methodName + ". Try again later.",
                        (int) result.getRetryAfterSeconds()
                    );
                }
            }
        }
        return joinPoint.proceed();
    }
}
