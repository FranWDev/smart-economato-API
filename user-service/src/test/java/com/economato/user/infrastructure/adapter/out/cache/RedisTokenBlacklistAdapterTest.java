package com.economato.user.infrastructure.adapter.out.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisTokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisTokenBlacklistAdapter(redisTemplate);
    }

    @Test
    void testBlacklistToken_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String token = "test.jwt.token";
        Date expiration = new Date(System.currentTimeMillis() + 3600000);

        adapter.blacklistToken(token, expiration);

        verify(valueOperations).set(eq("token_blacklist:" + token), eq("true"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testBlacklistToken_NullToken() {
        adapter.blacklistToken(null, new Date());
        verifyNoInteractions(valueOperations);
    }

    @Test
    void testIsBlacklisted_True() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("token_blacklist:bad.token")).thenReturn("true");

        assertTrue(adapter.isBlacklisted("bad.token"));
    }

    @Test
    void testIsBlacklisted_False() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("token_blacklist:good.token")).thenReturn(null);

        assertFalse(adapter.isBlacklisted("good.token"));
    }
}
