package com.placement.ratelimiter.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String BLOCKED_IP_CACHE = "blockedIpCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(BLOCKED_IP_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // Max limits to prevent OOM errors if under a massive distributed attack with millions of IPs
                .maximumSize(50_000) 
                // Short expiration. We only cache to handle immediate retries and reduce Redis overhead.
                // It should sync with retry-after but a generic 5 second TTL handles 99% of short-circuiting needs.
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .recordStats());
        return cacheManager;
    }
}
