package com.economato.inventory.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Custom CacheErrorHandler that gracefully handles Redis cache failures.
 * 
 * When a cache operation fails (e.g., Redis is unavailable), this handler:
 * - Logs the error
 * - Records the failure in the Redis circuit breaker
 * - Allows the method to proceed without caching
 * - Prevents the entire request from failing due to cache unavailability
 * 
 * This is Spring's idiomatic approach for handling cache failures.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisCacheErrorHandler implements CacheErrorHandler {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache GET operation failed for cache '{}', key '{}'. Proceeding without cache. Reason: {}",
                cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache PUT operation failed for cache '{}', key '{}'. Proceeding without caching. Reason: {}",
                cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache EVICT operation failed for cache '{}', key '{}'. Proceeding without evicting. Reason: {}",
                cache.getName(), key, exception.getMessage());
        recordRedisFailure(exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache CLEAR operation failed for cache '{}'. Proceeding without clearing. Reason: {}",
                cache.getName(), exception.getMessage());
        recordRedisFailure(exception);
    }

    /**
     * Record Redis cache failure in the circuit breaker.
     * This allows the circuit breaker to track failure rate and potentially open,
     * triggering WebSocket alerts to frontend.
     */
    private void recordRedisFailure(RuntimeException exception) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            circuitBreaker.onError(System.nanoTime(), TimeUnit.NANOSECONDS, exception);
        } catch (Exception e) {
            log.warn("Failed to record Redis failure in circuit breaker: {}", e.getMessage());
        }
    }
}
