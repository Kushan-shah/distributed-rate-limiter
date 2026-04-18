package com.placement.ratelimiter.rateLimiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token Bucket Algorithm Implementation.
 *
 * How it works:
 * - A "bucket" holds up to N tokens (N = capacity/limit).
 * - Each request consumes 1 token.
 * - Tokens refill gradually over the window period.
 * - If no tokens are left, the request is rejected (HTTP 429).
 * - Natively handles burst traffic: idle periods accumulate tokens.
 *
 * Why Lua Script?
 * All operations (read token count, calculate refill, consume token, set TTL)
 * execute atomically inside Redis, preventing race conditions in distributed systems.
 *
 * Redis Cluster Note:
 * This script accesses TWO keys (tokens + timestamp). Both keys are passed
 * via the KEYS[] array so Redis Cluster can validate slot assignment.
 * When deploying on Cluster, use hash tags in key construction to ensure
 * both keys land on the same slot: {rate_limit:user:123}:tokens and {rate_limit:user:123}:ts
 *
 * Redis Compatibility:
 * Timestamp is passed from Java via ARGV (not redis.call('TIME')) to avoid
 * the "Write commands not allowed after non deterministic commands" error
 * in Redis < 3.2. For Redis 7+ environments, TIME could be used with
 * redis.replicate_commands() or FUNCTION API.
 */
@Slf4j
@Component("TOKEN_BUCKET")
public class TokenBucketStrategy implements RateLimiterStrategy {

    private final RedisTemplate<String, Object> redisTemplate;

    /*
      Lua Script for Token Bucket:
      KEYS[1] = the token count key (e.g. rate_limit:user:123:/api/test)
      KEYS[2] = the timestamp key   (e.g. rate_limit:user:123:/api/test:ts)
      ARGV[1] = bucket capacity (max tokens)
      ARGV[2] = refill window in seconds
      ARGV[3] = current epoch seconds (passed from Java for Redis 3.x compatibility)
      Returns: { 1|0 (allowed), remaining_tokens, retry_after_seconds }
    */
    private static final String SCRIPT =
            "local tokens_key = KEYS[1] " +
            "local timestamp_key = KEYS[2] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local refill_time = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +

            "local last_tokens = tonumber(redis.call('get', tokens_key)) " +
            "if last_tokens == nil then last_tokens = capacity end " +
            "local last_refreshed = tonumber(redis.call('get', timestamp_key)) " +
            "if last_refreshed == nil then last_refreshed = now end " +

            "local delta = math.max(0, now - last_refreshed) " +
            "local refill_rate = capacity / refill_time " +
            "local filled_tokens = math.min(capacity, last_tokens + math.floor(delta * refill_rate)) " +

            "local allowed = 0 " +
            "local new_tokens = filled_tokens " +

            "if filled_tokens >= 1 then " +
            "    allowed = 1 " +
            "    new_tokens = filled_tokens - 1 " +
            "end " +

            "redis.call('setex', tokens_key, refill_time * 2, new_tokens) " +
            "redis.call('setex', timestamp_key, refill_time * 2, now) " +

            "return { allowed, new_tokens, refill_time }";

    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> redisScript;

    @SuppressWarnings("rawtypes")
    public TokenBucketStrategy(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>(SCRIPT, List.class);
    }

    @Override
    public RateLimitResult isAllowed(String key, int limit, int windowInSeconds) {
        try {
            String tsKey = key + ":ts";
            long nowSeconds = System.currentTimeMillis() / 1000;

            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) redisTemplate.execute(
                    redisScript,
                    List.of(key, tsKey),
                    String.valueOf(limit),
                    String.valueOf(windowInSeconds),
                    String.valueOf(nowSeconds)
            );

            if (result == null || result.size() < 3) {
                log.warn("Null or incomplete result from Redis for key {}", key);
                return new RateLimitResult(true, limit, limit, 0);
            }

            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);
            long retryAfter = result.get(2);

            return new RateLimitResult(allowed, remaining, limit, allowed ? 0 : retryAfter);
        } catch (Exception e) {
            log.warn("Redis fail-open for TokenBucket: {}", e.getMessage());
            return new RateLimitResult(true, limit, limit, 0);
        }
    }
}
