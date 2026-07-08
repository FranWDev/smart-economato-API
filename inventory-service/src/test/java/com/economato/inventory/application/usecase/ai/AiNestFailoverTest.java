package com.economato.inventory.application.usecase.ai;
import com.economato.inventory.application.usecase.shared.NestStreamBridgeService;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.shared.mcp.NestCompletionRequest;
import com.economato.inventory.application.dto.shared.mcp.ToolCallInfo;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiStreamException;
import com.economato.inventory.infrastructure.config.ai.ai.AiNestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;

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
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private I18nService i18nService;

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
                Optional.empty(),
                i18nService
        );
    }

    @Test
    void circuitBreakerHalfOpen_singleProbeRequest() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);
        when(nestRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.accept(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(NestCompletionRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.exchange(any())).thenReturn(new NestStreamBridgeService.StreamCompletionResult("ok", 1, 1, null, new ArrayList<ToolCallInfo>()));

        service.streamCompletion(
                new NestCompletionRequest("ctx", "key", "OPENAI", "Admin", "es", "gpt-4o"),
                new SseEmitter(),
                "jwt");

        verify(nestRestClient, times(1)).post();
        verify(circuitBreaker).onSuccess(anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void nestRecovers_circuitBreakerCloses() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(nestRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.accept(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(NestCompletionRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.exchange(any())).thenReturn(new NestStreamBridgeService.StreamCompletionResult("ok", 2, 3, null, new ArrayList<ToolCallInfo>()));

        service.streamCompletion(
                new NestCompletionRequest("ctx", "key", "OPENAI", "Admin", "es", "gpt-4o"),
                new SseEmitter(),
                "jwt");

        verify(circuitBreaker).onSuccess(anyLong(), eq(TimeUnit.MILLISECONDS));
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