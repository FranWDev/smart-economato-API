package com.economato.inventory.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;

import java.util.Collection;

@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerAwareCacheManager implements CacheManager {

    private final CacheManager redisCacheManager;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final CacheManager noOpCacheManager = new NoOpCacheManager();

    @Override
    public Cache getCache(String name) {
        if (shouldBypassRedis()) {
            return noOpCacheManager.getCache(name);
        }

        Cache cache = redisCacheManager.getCache(name);
        if (cache == null) {
            return noOpCacheManager.getCache(name);
        }

        return cache;
    }

    @Override
    public Collection<String> getCacheNames() {
        return redisCacheManager.getCacheNames();
    }

    private boolean shouldBypassRedis() {
        try {
            CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            boolean open = redisCircuitBreaker.getState() == CircuitBreaker.State.OPEN;
            if (open) {
                log.debug("Redis circuit breaker OPEN: using NoOp cache manager fallback");
            }
            return open;
        } catch (Exception ex) {
            log.warn("Unable to inspect Redis circuit breaker state, keeping Redis cache path: {}", ex.getMessage());
            return false;
        }
    }
}