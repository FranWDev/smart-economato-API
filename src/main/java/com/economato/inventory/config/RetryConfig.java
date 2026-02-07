package com.economato.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de Retry para operaciones que pueden fallar por concurrencia.
 * 
 * Implementa reintentos automáticos con backoff para excepciones de Optimistic Locking.
 */
@Configuration
@EnableRetry
public class RetryConfig {

    /**
     * Configura el RetryTemplate para operaciones concurrentes.
     * 
     * - Reintenta hasta 3 veces en caso de OptimisticLockException
     * - Espera 100ms entre cada reintento
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Configurar política de reintentos
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(OptimisticLockException.class, true);
        retryableExceptions.put(OptimisticLockingFailureException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Configurar backoff (tiempo entre reintentos)
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(100L); // 100ms
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
