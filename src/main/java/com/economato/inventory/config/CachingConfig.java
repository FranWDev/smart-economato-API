package com.economato.inventory.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Caching configuration that registers a custom CacheErrorHandler.
 * 
 * This replaces the problematic CustomCircuitBreakerAspect approach for cache operations.
 * Spring's CacheErrorHandler is the idiomatic way to handle cache failures gracefully.
 */
@Configuration
@Profile("!test")
public class CachingConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }
}
