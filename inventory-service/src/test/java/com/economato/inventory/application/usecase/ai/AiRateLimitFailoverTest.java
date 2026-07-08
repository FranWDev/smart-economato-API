package com.economato.inventory.application.usecase.ai;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatMessageRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.economato.inventory.infrastructure.config.ai.ai.AiRateLimitProperties;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRateLimitFailoverTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private CircuitBreaker redisCircuitBreaker;
    @Mock
    private AiChatRepository aiChatRepository;
    @Mock
    private AiChatMessageRepository aiChatMessageRepository;

    private AiRateLimitService service;
    private AiRateLimitProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiRateLimitProperties();
        properties.setMessagesPerMinute(3);
        properties.setMaxChatsPerUser(2);
        properties.setMaxMessagesPerChat(4);
        properties.setMaxApiKeysPerUser(5);
        properties.setFailOpen(true);

        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service = new AiRateLimitService(
                properties,
                stringRedisTemplate,
                circuitBreakerRegistry,
                aiChatRepository,
                aiChatMessageRepository,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void redisDown_failOpenTrue_allowsRequests() {
        properties.setFailOpen(true);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        assertTrue(service.isAllowed(10));
        verify(stringRedisTemplate, never()).opsForZSet();
    }

    @Test
    void redisDown_failOpenFalse_rejectsRequests() {
        properties.setFailOpen(false);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        assertFalse(service.isAllowed(10));
        verify(stringRedisTemplate, never()).opsForZSet();
    }

    @Test
    void redisException_failOpenFalse_rejectsRequests() {
        properties.setFailOpen(false);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("redis down"));

        assertFalse(service.isAllowed(10));
    }

    @Test
    void redisRecovers_rateLimitResumes() {
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN, CircuitBreaker.State.CLOSED);

        assertTrue(service.isAllowed(10));

        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(anyString())).thenReturn(0L);

        assertTrue(service.isAllowed(10));
    }

    @Test
    void redisDown_circuitBreakerOpens() {
        properties.setFailOpen(false);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("redis connection failed"));

        assertFalse(service.isAllowed(10));
        verify(redisCircuitBreaker).onError(anyLong(), any(java.util.concurrent.TimeUnit.class), any(Throwable.class));
    }

    @Test
    void slidingWindow_expiresCorrectly() {
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(1L);
        when(zSetOperations.zCard(anyString())).thenReturn(1L, 0L);

        assertTrue(service.isAllowed(10));
        assertTrue(service.isAllowed(10));
    }
}