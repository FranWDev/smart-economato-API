package com.economato.inventory.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Caching configuration that registers a custom CacheErrorHandler.
 * 
 * This replaces the problematic CustomCircuitBreakerAspect approach for cache operations.
 * Spring's CacheErrorHandler is the idiomatic way to handle cache failures gracefully.
 * Cache failures are recorded in the Redis circuit breaker for tracking and alerting.
 */
@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class CachingConfig implements CachingConfigurer {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler(circuitBreakerRegistry);
    }
}
