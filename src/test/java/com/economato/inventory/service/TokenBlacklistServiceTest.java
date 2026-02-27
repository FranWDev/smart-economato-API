package com.economato.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    private RedisTokenBlacklistService tokenBlacklistService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new RedisTokenBlacklistService(redisTemplate);
    }

    @Test
    void testBlacklistToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String token = "test.jwt.token";
        Date expiration = new Date(System.currentTimeMillis() + 3600000); // 1 hour in future

        tokenBlacklistService.blacklistToken(token, expiration);

        verify(valueOperations).set(
                eq("token_blacklist:" + token),
                eq("revoked"),
                any(Duration.class));
    }

    @Test
    void testIsBlacklistedReturnsTrue() {
        String token = "blacklisted.token";
        when(redisTemplate.hasKey("token_blacklist:" + token)).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted(token));
    }

    @Test
    void testIsBlacklistedReturnsFalse() {
        String token = "non.blacklisted.token";
        when(redisTemplate.hasKey("token_blacklist:" + token)).thenReturn(false);

        assertFalse(tokenBlacklistService.isBlacklisted(token));
    }

    @Test
    void testBlacklistTokenWithNullToken() {
        tokenBlacklistService.blacklistToken(null, new Date());
        verifyNoInteractions(valueOperations);
    }

    @Test
    void testBlacklistTokenWithExpiredDate() {
        String token = "expired.token";
        Date expiration = new Date(System.currentTimeMillis() - 1000); // Past

        tokenBlacklistService.blacklistToken(token, expiration);

        verifyNoInteractions(valueOperations);
    }

    @Test
    void testGetBlacklistSize() {
        Set<String> keys = Set.of("key1", "key2");
        when(redisTemplate.keys(anyString())).thenReturn(keys);

        assertEquals(2, tokenBlacklistService.getBlacklistSize());
    }

    @Test
    void testClearBlacklist() {
        Set<String> keys = Set.of("key1", "key2");
        when(redisTemplate.keys(anyString())).thenReturn(keys);

        tokenBlacklistService.clearBlacklist();

        verify(redisTemplate).delete(keys);
    }
}
