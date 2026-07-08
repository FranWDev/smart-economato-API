package com.economato.inventory.application.usecase.user;

import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.economato.inventory.domain.model.user.RevokedToken;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.RevokedTokenRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.github.benmanes.caffeine.cache.Cache;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de lista negra de tokens que utiliza Redis como caché primaria con
 * retroceso automático a la base de datos.
 * 
 * Cuando Redis está disponible: Almacena tokens en Redis con TTL
 * Cuando Redis falla: Retrocede a verificar en la base de datos
 * 
 * Esto previene errores 401 cuando el circuit breaker de Redis está abierto.
 */
@Slf4j
@Service
@Profile("!test")
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RevokedTokenRepository revokedTokenRepository;
    private final Cache<String, Locale> tokenLocaleCache;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final I18nService i18nService;
    private static final String BLACKLIST_PREFIX = "token_blacklist:";

    public RedisTokenBlacklistService(RedisTemplate<String, String> redisTemplate,
            RevokedTokenRepository revokedTokenRepository,
            Cache<String, Locale> tokenLocaleCache,
            CircuitBreakerRegistry circuitBreakerRegistry,
            I18nService i18nService) {
        this.redisTemplate = redisTemplate;
        this.revokedTokenRepository = revokedTokenRepository;
        this.tokenLocaleCache = tokenLocaleCache;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.i18nService = i18nService;
    }

    @Override
    public void blacklistToken(String token, Date expirationDate) {
        if (token != null && !token.isEmpty() && expirationDate != null) {
            if (!isRedisCircuitOpen()) {
                try {
                    long ttlMillis = expirationDate.getTime() - System.currentTimeMillis();
                    if (ttlMillis > 0) {
                        redisTemplate.opsForValue().set(
                                BLACKLIST_PREFIX + token,
                                "revoked",
                                Duration.ofMillis(ttlMillis));
                        recordSuccess();
                    }
                } catch (Exception e) {
                    log.warn("Failed to blacklist token in Redis, falling back to database: {}", e.getMessage());
                    recordFailure(e);
                }
            } else {
                log.debug("Redis circuit breaker OPEN, skipping Redis blacklist write");
            }

            try {
                revokedTokenRepository.save(new RevokedToken(token, expirationDate));
            } catch (Exception e) {
                log.error("Failed to blacklist token in database: {}", e.getMessage());
                throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_AUTH_TOKEN_REVOKE_FAILED), e);
            }

            tokenLocaleCache.invalidate(token);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        if (!isRedisCircuitOpen()) {
            try {
                Boolean exists = redisTemplate.hasKey(BLACKLIST_PREFIX + token);
                recordSuccess();
                if (exists != null && exists) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("Redis unavailable for token lookup, falling back to database: {}", e.getMessage());
                recordFailure(e);
            }
        } else {
            log.debug("Redis circuit breaker OPEN, skipping Redis blacklist lookup");
        }

        try {
            return revokedTokenRepository.existsByToken(token);
        } catch (Exception e) {
            log.error("Failed to check token blacklist in database: {}", e.getMessage());
            log.warn("Both Redis and Database failed for token check. Denying access for token to be safe.");
            return true;
        }
    }

    @Override
    public void clearBlacklist() {
        if (!isRedisCircuitOpen()) {
            try {
                var keys = redisTemplate.keys(BLACKLIST_PREFIX + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
                recordSuccess();
            } catch (Exception e) {
                log.warn("Failed to clear Redis blacklist: {}", e.getMessage());
                recordFailure(e);
            }
        } else {
            log.debug("Redis circuit breaker OPEN, skipping Redis blacklist clear");
        }

        try {
            revokedTokenRepository.deleteAll();
        } catch (Exception e) {
            log.error("Failed to clear database blacklist: {}", e.getMessage());
        }
    }

    @Override
    public int getBlacklistSize() {
        if (!isRedisCircuitOpen()) {
            try {
                var keys = redisTemplate.keys(BLACKLIST_PREFIX + "*");
                recordSuccess();
                if (keys != null && !keys.isEmpty()) {
                    return keys.size();
                }
            } catch (Exception e) {
                log.debug("Failed to get Redis blacklist size: {}", e.getMessage());
                recordFailure(e);
            }
        } else {
            log.debug("Redis circuit breaker OPEN, skipping Redis blacklist size check");
        }

        try {
            return (int) revokedTokenRepository.count();
        } catch (Exception e) {
            log.error("Failed to get database blacklist size: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void cleanExpiredTokens() {
        try {
            int deletedCount = revokedTokenRepository.deleteExpiredTokens(new Date());
            if (deletedCount > 0) {
                log.debug("Cleaned {} expired tokens from database", deletedCount);
            }
        } catch (Exception e) {
            log.error("Failed to clean expired tokens: {}", e.getMessage());
        }
    }

    private boolean isRedisCircuitOpen() {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
        } catch (Exception e) {
            log.warn("Unable to inspect Redis circuit breaker state: {}", e.getMessage());
            return false;
        }
    }

    private void recordSuccess() {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            circuitBreaker.onSuccess(0, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to record Redis success: {}", e.getMessage());
        }
    }

    private void recordFailure(Throwable exception) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            Throwable rootCause = resolveRootCause(exception);
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, rootCause);
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
