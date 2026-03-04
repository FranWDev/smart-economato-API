package com.economato.inventory.security;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.economato.inventory.model.RevokedToken;
import com.economato.inventory.repository.RevokedTokenRepository;
import com.economato.inventory.service.RedisTokenBlacklistService;
import com.economato.inventory.service.TokenBlacklistService;

import java.time.Duration;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test to verify that token blacklist works correctly when Redis is down.
 * The RedisTokenBlacklistService should automatically fallback to the database.
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterRedisFailoverTest {

    private RedisTokenBlacklistService tokenBlacklistService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private Cache<String, Locale> tokenLocaleCache;

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker redisCircuitBreaker;

    @Mock
    private UserDetailsService userDetailsService;

    private JwtFilter jwtFilter;

    @Mock
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        lenient().when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        lenient().when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        tokenBlacklistService = new RedisTokenBlacklistService(
                redisTemplate,
                revokedTokenRepository,
                tokenLocaleCache,
                circuitBreakerRegistry);
        jwtFilter = new JwtFilter(jwtUtils, userDetailsService, tokenBlacklistService);
    }

    @Test
    void testIsBlacklistedSkipsRedisWhenCircuitBreakerOpen() {
        String token = "test.jwt.token";
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(revokedTokenRepository.existsByToken(token)).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted(token));

        verifyNoInteractions(redisTemplate);
        verify(revokedTokenRepository).existsByToken(token);
    }

    @Test
    void testIsBlacklistedFallsBackToDatabaseWhenRedisDown() {
        String token = "test.jwt.token";

        // Simulate Redis being down by throwing exception
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis connection failed"));

        // Token IS blacklisted in database
        when(revokedTokenRepository.existsByToken(token)).thenReturn(true);

        // Verify that isBlacklisted still returns true by checking database
        assertTrue(tokenBlacklistService.isBlacklisted(token));

        // Verify Redis was tried first
        verify(redisTemplate).hasKey(anyString());

        // Verify database was used as fallback
        verify(revokedTokenRepository).existsByToken(token);
    }

    @Test
    void testIsBlacklistedFallsBackToDatabaseWhenTokenNotInRedis() {
        String token = "test.jwt.token";

        // Redis returns false (token not blacklisted in Redis)
        when(redisTemplate.hasKey("token_blacklist:" + token)).thenReturn(false);

        // But token IS blacklisted in database
        when(revokedTokenRepository.existsByToken(token)).thenReturn(true);

        // Verify that isBlacklisted still returns true by checking database
        assertTrue(tokenBlacklistService.isBlacklisted(token));

        // Verify database was used as fallback
        verify(revokedTokenRepository).existsByToken(token);
    }

    @Test
    void testIsBlacklistedReturnsFalseWhenTokenNotBlacklistedAnywhere() {
        String token = "valid.jwt.token";

        // Token not in Redis
        when(redisTemplate.hasKey("token_blacklist:" + token)).thenReturn(false);

        // Token not in database
        when(revokedTokenRepository.existsByToken(token)).thenReturn(false);

        // Verify that isBlacklisted returns false
        assertFalse(tokenBlacklistService.isBlacklisted(token));

        // Verify both were checked
        verify(redisTemplate).hasKey(anyString());
        verify(revokedTokenRepository).existsByToken(token);
    }

    @Test
    void testBlacklistTokenSavesBothToRedisAndDatabase() throws Exception {
        String token = "test.jwt.token";
        Date expiration = new Date(System.currentTimeMillis() + 3600000);

        when(redisTemplate.opsForValue()).thenReturn(mock());

        tokenBlacklistService.blacklistToken(token, expiration);

        // Verify token was saved to database
        verify(revokedTokenRepository).save(any(RevokedToken.class));

        // Verify locale cache was invalidated
        verify(tokenLocaleCache).invalidate(token);
    }

    @Test
    void testBlacklistTokenContinuesEvenIfRedisFailsForSave() {
        String token = "test.jwt.token";
        Date expiration = new Date(System.currentTimeMillis() + 3600000);

        // Simulate Redis being down
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection failed"));

        // This should NOT throw an exception - it should save to database and continue
        assertDoesNotThrow(() -> tokenBlacklistService.blacklistToken(token, expiration));

        // Verify token was still saved to database despite Redis failure
        verify(revokedTokenRepository).save(any(RevokedToken.class));
    }

    @Test
    void testFailingBothRedisAndDatabaseForIsBlacklistedDeniesAccessSecurely() {
        String token = "test.jwt.token";

        // Both Redis and database fail
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis down"));
        when(revokedTokenRepository.existsByToken(token)).thenThrow(new RuntimeException("Database down"));

        // In case of both failures, we DENY access (fail-secure)
        assertTrue(tokenBlacklistService.isBlacklisted(token));
    }
}
