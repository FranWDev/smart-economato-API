package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.AiChatStatus;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AiChatRepository;
import com.economato.inventory.infrastructure.config.ai.AiRateLimitProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRateLimitService {

    private static final String RATE_LIMIT_KEY_PREFIX = "ai:ratelimit:";
    private static final long WINDOW_MILLIS = 60_000L;

    private final AiRateLimitProperties aiRateLimitProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final AiChatRepository aiChatRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final MeterRegistry meterRegistry;

    public boolean isAllowed(Integer userId) {
        if (userId == null) {
            return false;
        }

        if (isRedisCircuitOpen()) {
            boolean allowed = Boolean.TRUE.equals(aiRateLimitProperties.getFailOpen());
            if (!allowed) {
                recordRejected("per_minute");
            }
            return allowed;
        }

        long now = Instant.now().toEpochMilli();
        long threshold = now - WINDOW_MILLIS;
        String key = buildKey(userId);

        try {
            stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, threshold);
            Long count = stringRedisTemplate.opsForZSet().zCard(key);
            recordSuccess();
            boolean allowed = count == null || count < aiRateLimitProperties.getMessagesPerMinute();
            if (!allowed) {
                recordRejected("per_minute");
            }
            return allowed;
        } catch (Exception ex) {
            log.debug("Redis rate-limit check failed for user {}: {}", userId, ex.getMessage());
            recordFailure(ex);
            boolean allowed = Boolean.TRUE.equals(aiRateLimitProperties.getFailOpen());
            if (!allowed) {
                recordRejected("per_minute");
            }
            return allowed;
        }
    }

    public void recordRequest(Integer userId) {
        if (userId == null) {
            return;
        }

        if (isRedisCircuitOpen()) {
            return;
        }

        long now = Instant.now().toEpochMilli();
        String key = buildKey(userId);
        String member = now + ":" + UUID.randomUUID();

        try {
            stringRedisTemplate.opsForZSet().add(key, member, now);
            stringRedisTemplate.expire(key, 2, TimeUnit.MINUTES);
            recordSuccess();
        } catch (Exception ex) {
            log.debug("Redis rate-limit record failed for user {}: {}", userId, ex.getMessage());
            recordFailure(ex);
        }
    }

    public boolean canCreateChat(Integer userId) {
        if (userId == null) {
            return false;
        }
        long activeChats = aiChatRepository.countByUserIdAndStatus(userId, AiChatStatus.ACTIVE);
        boolean allowed = activeChats < aiRateLimitProperties.getMaxChatsPerUser();
        if (!allowed) {
            recordRejected("max_chats");
        }
        return allowed;
    }

    public boolean canSendMessage(Long chatId) {
        if (chatId == null) {
            return false;
        }
        long messages = aiChatMessageRepository.countByChatId(chatId);
        boolean allowed = messages < aiRateLimitProperties.getMaxMessagesPerChat();
        if (!allowed) {
            recordRejected("max_messages");
        }
        return allowed;
    }

    private String buildKey(Integer userId) {
        return RATE_LIMIT_KEY_PREFIX + userId;
    }

    private boolean isRedisCircuitOpen() {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
        } catch (Exception ex) {
            log.warn("Unable to inspect Redis circuit breaker state: {}", ex.getMessage());
            return false;
        }
    }

    private void recordSuccess() {
        try {
            circuitBreakerRegistry.circuitBreaker("redis").onSuccess(0, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            log.debug("Failed to record redis circuit success: {}", ex.getMessage());
        }
    }

    private void recordFailure(Throwable exception) {
        try {
            circuitBreakerRegistry.circuitBreaker("redis").onError(0, TimeUnit.MILLISECONDS, resolveRootCause(exception));
        } catch (Exception ex) {
            log.debug("Failed to record redis circuit failure: {}", ex.getMessage());
        }
    }

    private Throwable resolveRootCause(Throwable exception) {
        Throwable current = exception;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current != null ? current : exception;
    }

    private void recordRejected(String reason) {
        meterRegistry.counter("ai.ratelimit.rejected.total", "reason", reason).increment();
    }
}
