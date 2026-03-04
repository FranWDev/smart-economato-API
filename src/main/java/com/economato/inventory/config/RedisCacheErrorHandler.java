package com.economato.inventory.config;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Custom CacheErrorHandler that gracefully handles Redis cache failures.
 * 
 * When a cache operation fails (e.g., Redis is unavailable), this handler:
 * - Logs the error
 * - Allows the method to proceed without caching
 * - Prevents the entire request from failing due to cache unavailability
 * 
 * This is Spring's idiomatic approach for handling cache failures.
 */
@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache GET operation failed for cache '{}', key '{}'. Proceeding without cache. Reason: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache PUT operation failed for cache '{}', key '{}'. Proceeding without caching. Reason: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache EVICT operation failed for cache '{}', key '{}'. Proceeding without evicting. Reason: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache CLEAR operation failed for cache '{}'. Proceeding without clearing. Reason: {}",
                cache.getName(), exception.getMessage());
    }
}
