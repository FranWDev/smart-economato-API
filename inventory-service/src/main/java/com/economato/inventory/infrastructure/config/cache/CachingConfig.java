package com.economato.inventory.infrastructure.config.cache;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuración de caché que registra un CacheErrorHandler personalizado.
 * 
 * Esto reemplaza el enfoque problemático de CustomCircuitBreakerAspect para las operaciones de caché.
 * CacheErrorHandler de Spring es la forma idiomática de manejar fallos de caché de manera elegante.
 * Los fallos de caché se registran en el circuito de Redis para su seguimiento y alertas.
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
