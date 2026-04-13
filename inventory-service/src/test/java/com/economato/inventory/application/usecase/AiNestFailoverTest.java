package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.mcp.NestCompletionRequest;
import com.economato.inventory.infrastructure.adapter.in.web.AiStreamException;
import com.economato.inventory.infrastructure.config.ai.AiNestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiNestFailoverTest {

    @Mock
    private RestClient nestRestClient;
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock
    private CircuitBreaker circuitBreaker;
    @Mock
    private ClientHttpResponse clientHttpResponse;

    private NestStreamBridgeService service;

    @BeforeEach
    void setUp() {
        AiNestProperties properties = new AiNestProperties();
        properties.setBaseUrl("http://nest");
        properties.setServiceKey("service-key");
        properties.setAllowedOrigin("http://localhost");
        properties.setStreamTimeoutMs(5000L);
        properties.setCompletionEndpoint("/api/completion");

        when(circuitBreakerRegistry.circuitBreaker("nest")).thenReturn(circuitBreaker);

        service = new NestStreamBridgeService(
                nestRestClient,
                properties,
                circuitBreakerRegistry,
                new SimpleMeterRegistry(),
                new ObjectMapper(),
                Optional.empty()
        );
    }

    @Test
    void circuitBreakerOpen_failsFastWithoutCallingNest() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        assertThrows(AiStreamException.class, () -> service.streamCompletion(
                new NestCompletionRequest("ctx", "key", "OPENAI", "Admin", "es", "gpt-4o"),
                new SseEmitter(),
                "jwt"));

        verify(nestRestClient, never()).post();
    }

    @Test
    void nestConnectionFailure_recordsCircuitBreakerError() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(nestRestClient.post()).thenThrow(new RuntimeException("connect timeout"));

        assertThrows(AiStreamException.class, () -> service.streamCompletion(
                new NestCompletionRequest("ctx", "key", "OPENAI", "Admin", "es", "gpt-4o"),
                new SseEmitter(),
                "jwt"));

        verify(circuitBreaker).onError(anyLong(), eq(TimeUnit.MILLISECONDS), any(Throwable.class));
    }

    @Test
    void nestTimeoutFailure_treatedAsStreamException() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(nestRestClient.post()).thenThrow(new RuntimeException("read timeout"));

        assertThrows(AiStreamException.class, () -> service.streamCompletion(
                new NestCompletionRequest("ctx", "key", "OPENAI", "Admin", "es", "gpt-4o"),
                new SseEmitter(),
                "jwt"));
    }
}