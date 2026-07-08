package com.economato.inventory.application.usecase.ai;

import com.economato.inventory.domain.model.ai.AiChatStatus;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiRateLimitProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AiRateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock
    private AiChatRepository aiChatRepository;
    @Mock
    private AiChatMessageRepository aiChatMessageRepository;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private CircuitBreaker redisCircuitBreaker;

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

        lenient().when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        lenient().when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

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
    void isAllowed_underLimit_returnsTrue() {
        when(zSetOperations.zCard(anyString())).thenReturn(2L);

        boolean allowed = service.isAllowed(10);

        assertTrue(allowed);
        verify(zSetOperations).removeRangeByScore(anyString(), anyDouble(), anyDouble());
    }

    @Test
    void isAllowed_overLimit_returnsFalse() {
        when(zSetOperations.zCard(anyString())).thenReturn(4L);

        boolean allowed = service.isAllowed(10);

        assertFalse(allowed);
    }

    @Test
    void isAllowed_redisDown_failOpen_returnsTrue() {
        properties.setFailOpen(true);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        boolean allowed = service.isAllowed(10);

        assertTrue(allowed);
        verify(stringRedisTemplate, never()).opsForZSet();
    }

    @Test
    void isAllowed_redisDown_failClosed_returnsFalse() {
        properties.setFailOpen(false);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        boolean allowed = service.isAllowed(10);

        assertFalse(allowed);
        verify(stringRedisTemplate, never()).opsForZSet();
    }

    @Test
    void isAllowed_redisException_fallsBackGracefully() {
        properties.setFailOpen(false);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("redis down"));

        boolean allowed = service.isAllowed(10);

        assertFalse(allowed);
    }

    @Test
    void recordRequest_addsToSortedSet() {
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        service.recordRequest(10);

        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
        verify(stringRedisTemplate).expire(anyString(), eq(2L), eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    void isAllowed_windowExpires_allowsAgain() {
        when(zSetOperations.zCard(anyString())).thenReturn(4L, 0L);

        boolean first = service.isAllowed(10);
        boolean second = service.isAllowed(10);

        assertFalse(first);
        assertTrue(second);
        verify(zSetOperations, times(2)).removeRangeByScore(anyString(), anyDouble(), anyDouble());
    }

    @Test
    void canCreateChat_underLimit_returnsTrue() {
        when(aiChatRepository.countByUserIdAndStatus(10, AiChatStatus.ACTIVE)).thenReturn(1L);

        boolean allowed = service.canCreateChat(10);

        assertTrue(allowed);
    }

    @Test
    void canCreateChat_overLimit_returnsFalse() {
        when(aiChatRepository.countByUserIdAndStatus(10, AiChatStatus.ACTIVE)).thenReturn(2L);

        boolean allowed = service.canCreateChat(10);

        assertFalse(allowed);
    }

    @Test
    void canSendMessage_underLimit_returnsTrue() {
        when(aiChatMessageRepository.countByChatId(99L)).thenReturn(3L);

        boolean allowed = service.canSendMessage(99L);

        assertTrue(allowed);
    }

    @Test
    void canSendMessage_overLimit_returnsFalse() {
        when(aiChatMessageRepository.countByChatId(99L)).thenReturn(4L);

        boolean allowed = service.canSendMessage(99L);

        assertFalse(allowed);
    }
}
