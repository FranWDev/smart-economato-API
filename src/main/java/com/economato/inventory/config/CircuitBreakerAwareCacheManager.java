package com.economato.inventory.config;

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
        Cache cache = redisCacheManager.getCache(name);
        if (cache == null) {
            return noOpCacheManager.getCache(name);
        }

        // Wrap cache to check circuit breaker before every operation
        return new CircuitBreakerAwareCache(cache, circuitBreakerRegistry);
    }

    @Override
    public Collection<String> getCacheNames() {
        return redisCacheManager.getCacheNames();
    }
}