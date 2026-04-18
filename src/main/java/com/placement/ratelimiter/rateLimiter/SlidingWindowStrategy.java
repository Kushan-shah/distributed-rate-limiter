package com.placement.ratelimiter.rateLimiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sliding Window Log Algorithm Implementation.
 *
 * How it works:
 * - Uses a Redis Sorted Set (ZSET) where each request timestamp is a member.
 * - On each request, we remove all entries older than the window boundary.
 * - If the remaining count is under the limit, the request is allowed and added.
 * - Provides precise, smooth rate limiting with no boundary burst issues.
 *
 * Trade-off vs Token Bucket:
 * - More memory intensive (stores each request timestamp).
 * - Stricter enforcement (no burst allowance).
 * - Better for critical endpoints like payment APIs.
 *
 * Redis Compatibility:
 * Timestamps are passed from Java via ARGV to avoid the Redis 3.x limitation
 * where write commands are forbidden after non-deterministic commands like TIME.
 */
@Slf4j
@Component("SLIDING_WINDOW")
public class SlidingWindowStrategy implements RateLimiterStrategy {

    private final RedisTemplate<String, Object> redisTemplate;

    /*
      Lua Script for Sliding Window Log:
      KEYS[1] = the rate limit key
      ARGV[1] = limit
      ARGV[2] = window in seconds
      ARGV[3] = current timestamp in ms (passed from Java)
      ARGV[4] = window start timestamp in ms (passed from Java)
      ARGV[5] = unique member value (timestamp + suffix to avoid ZADD collisions)
      Returns: { 1|0 (allowed), remaining, window_seconds }
    */
    private static final String SCRIPT =
            "local key = KEYS[1] " +
            "local limit = tonumber(ARGV[1]) " +
            "local window_sec = tonumber(ARGV[2]) " +
            "local now_ms = tonumber(ARGV[3]) " +
            "local window_start_ms = tonumber(ARGV[4]) " +
            "local member = ARGV[5] " +

            "redis.call('zremrangebyscore', key, '-inf', window_start_ms) " +

            "local current_requests = redis.call('zcard', key) " +

            "local allowed = 0 " +
            "if current_requests < limit then " +
            "    redis.call('zadd', key, now_ms, member) " +
            "    allowed = 1 " +
            "    current_requests = current_requests + 1 " +
            "end " +

            "redis.call('expire', key, window_sec + 1) " +

            "local remaining = math.max(0, limit - current_requests) " +
            "return { allowed, remaining, window_sec } ";

    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> redisScript;

    @SuppressWarnings("rawtypes")
    public SlidingWindowStrategy(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>(SCRIPT, List.class);
    }

    @Override
    public RateLimitResult isAllowed(String key, int limit, int windowInSeconds) {
        try {
            long nowMs = System.currentTimeMillis();
            long windowStartMs = nowMs - (windowInSeconds * 1000L);
            String uniqueMember = nowMs + ":" + Thread.currentThread().getId() + ":" + System.nanoTime();

            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) redisTemplate.execute(
                    redisScript,
                    Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(windowInSeconds),
                    String.valueOf(nowMs),
                    String.valueOf(windowStartMs),
                    uniqueMember
            );

            if (result == null || result.size() < 3) {
                return new RateLimitResult(true, limit, limit, 0);
            }

            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);
            long retryAfter = result.get(2);

            return new RateLimitResult(allowed, remaining, limit, allowed ? 0 : retryAfter);
        } catch (Exception e) {
            log.warn("Redis fail-open for SlidingWindow: {}", e.getMessage());
            return new RateLimitResult(true, limit, limit, 0);
        }
    }
}
