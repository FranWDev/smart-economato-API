package com.economato.inventory.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Handles Redis cache failures by logging, recording in circuit breaker, and allowing degraded operation.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisCacheErrorHandler implements CacheErrorHandler {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        checkCircuitBreaker();
        log.warn("Cache GET failed for '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        checkCircuitBreaker();
        log.warn("Cache PUT failed for '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        checkCircuitBreaker();
        log.warn("Cache EVICT failed for '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        checkCircuitBreaker();
        log.warn("Cache CLEAR failed for '{}': {}", cache.getName(), exception.getMessage());
        recordRedisFailure(exception);
    }

    private void checkCircuitBreaker() {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                throw new RuntimeException("Redis circuit breaker OPEN");
            }
        } catch (RuntimeException e) {
            throw e; 
        } catch (Exception e) {
            log.warn("Error checking circuit breaker: {}", e.getMessage());
        }
    }

    private void recordRedisFailure(RuntimeException exception) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            circuitBreaker.onError(System.nanoTime(), TimeUnit.NANOSECONDS, exception);
        } catch (Exception e) {
            log.warn("Failed to record failure: {}", e.getMessage());
        }
    }
}

