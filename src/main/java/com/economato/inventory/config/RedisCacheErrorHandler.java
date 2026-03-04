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
        if (checkCircuitBreaker()) return;
        log.warn("Cache GET failed for '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        if (checkCircuitBreaker()) return;
        log.warn("Cache PUT failed for '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        if (checkCircuitBreaker()) return;
        log.warn("Cache EVICT failed for '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        if (checkCircuitBreaker()) return;
        log.warn("Cache CLEAR failed for '{}': {}", cache.getName(), exception.getMessage());
        recordRedisFailure(exception);
    }

    private boolean checkCircuitBreaker() {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.debug("Redis circuit breaker is OPEN, skipping error recording");
                return true;
            }
        } catch (Exception e) {
            log.warn("Error checking circuit breaker: {}", e.getMessage());
        }
        return false;
    }

    private void recordRedisFailure(RuntimeException exception) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            Throwable rootCause = resolveRootCause(exception);
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, rootCause);
        } catch (Exception e) {
            log.warn("Failed to record failure: {}", e.getMessage());
        }
    }

    private Throwable resolveRootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}

