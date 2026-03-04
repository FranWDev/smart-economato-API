package com.economato.inventory.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

/**
 * Cache wrapper that checks Redis circuit breaker before every operation.
 * When circuit breaker is OPEN, returns null (cache miss) to bypass Redis entirely.
 */
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerAwareCache implements Cache {

    private final Cache delegate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        if (isRedisCircuitOpen()) {
            log.debug("Redis CB OPEN: bypassing cache GET for key '{}'", key);
            return null; // Cache miss
        }
        
        try {
            ValueWrapper result = delegate.get(key);
            recordSuccess();
            return result;
        } catch (Exception e) {
            log.debug("Cache GET error for key '{}', returning null: {}", key, e.getMessage());
            recordFailure(e);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        if (isRedisCircuitOpen()) {
            log.debug("Redis CB OPEN: bypassing cache GET for key '{}'", key);
            return null;
        }
        
        try {
            T result = delegate.get(key, type);
            recordSuccess();
            return result;
        } catch (Exception e) {
            log.debug("Cache GET error for key '{}', returning null: {}", key, e.getMessage());
            recordFailure(e);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        if (isRedisCircuitOpen()) {
            log.debug("Redis CB OPEN: bypassing cache, calling valueLoader directly");
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }
        
        try {
            T result = delegate.get(key, valueLoader);
            recordSuccess();
            return result;
        } catch (Exception e) {
            log.debug("Cache GET error for key '{}': {}", key, e.getMessage());
            recordFailure(e);
            // Fall back to calling valueLoader directly
            try {
                return valueLoader.call();
            } catch (Exception ex) {
                throw new ValueRetrievalException(key, valueLoader, ex);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (isRedisCircuitOpen()) {
            log.debug("Redis CB OPEN: bypassing cache PUT for key '{}'", key);
            return;
        }
        
        try {
            delegate.put(key, value);
            recordSuccess();
        } catch (Exception e) {
            log.debug("Cache PUT error for key '{}': {}", key, e.getMessage());
            recordFailure(e);
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        if (isRedisCircuitOpen()) {
            log.debug("Redis CB OPEN: bypassing cache putIfAbsent for key '{}'", key);
            return null;
        }
        
        try {
            ValueWrapper result = delegate.putIfAbsent(key, value);
            recordSuccess();
            return result;
        } catch (Exception e) {
            log.debug("Cache putIfAbsent error for key '{}': {}", key, e.getMessage());
            recordFailure(e);
            return null;
        }
    }

    @Override
    public void evict(Object key) {
        if (isRedisCircuitOpen()) {
            log.debug("Redis CB OPEN: bypassing cache EVICT for key '{}'", key);
            return;
        }
        
        try {
            delegate.evict(key);
            recordSuccess();
        } catch (Exception e) {
            log.debug("Cache EVICT error for key '{}': {}", key, e.getMessage());
            recordFailure(e);
        }
    }

    @Override
    public void clear() {
        if (isRedisCircuitOpen()) {
            log.debug("Redis CB OPEN: bypassing cache CLEAR");
            return;
        }
        
        try {
            delegate.clear();
            recordSuccess();
        } catch (Exception e) {
            log.debug("Cache CLEAR error: {}", e.getMessage());
            recordFailure(e);
        }
    }

    private boolean isRedisCircuitOpen() {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redis");
            return cb.getState() == CircuitBreaker.State.OPEN 
                || cb.getState() == CircuitBreaker.State.FORCED_OPEN;
        } catch (Exception e) {
            log.warn("Error checking Redis CB state: {}", e.getMessage());
            return false;
        }
    }

    private void recordSuccess() {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redis");
            cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to record Redis success: {}", e.getMessage());
        }
    }

    private void recordFailure(Throwable exception) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redis");
            Throwable rootCause = resolveRootCause(exception);
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, rootCause);
        } catch (Exception e) {
            log.warn("Failed to record Redis failure: {}", e.getMessage());
        }
    }

    private Throwable resolveRootCause(Throwable exception) {
        Throwable current = exception;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current != null ? current : exception;
    }
}
