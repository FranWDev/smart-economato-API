package com.economato.inventory.application.usecase;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.mcp.NestCompletionRequest;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiStreamException;
import com.economato.inventory.infrastructure.config.ai.AiNestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class NestStreamBridgeServiceTest {

    @Mock
    private RestClient nestRestClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock
    private CircuitBreaker circuitBreaker;

    private NestStreamBridgeService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        AiNestProperties props = new AiNestProperties();
        props.setBaseUrl("http://localhost:9999");
        props.setServiceKey("test-service-key");
        props.setAllowedOrigin("http://localhost:9999");
        props.setCompletionEndpoint("/api/completion");

        meterRegistry = new SimpleMeterRegistry();
        when(circuitBreakerRegistry.circuitBreaker("nest")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        service = new NestStreamBridgeService(
                nestRestClient,
                props,
                circuitBreakerRegistry,
                meterRegistry,
                new ObjectMapper(),
                Optional.empty()
        );
    }

    @Test
    void streamCompletion_circuitBreakerOpen_throwsImmediately() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        assertThrows(AiStreamException.class,
                () -> service.streamCompletion(request(), new SseEmitter(1000L), "jwt-token"));
    }

    @Test
    void streamCompletion_successfulStream_returnsTokensAndText() throws Exception {
        mockRequestChain();
        when(requestBodySpec.exchange(any()))
                .thenReturn(new NestStreamBridgeService.StreamCompletionResult("hola mundo", 11, 7));

        NestStreamBridgeService.StreamCompletionResult result =
                service.streamCompletion(request(), new SseEmitter(5000L), "jwt-token");

        assertNotNull(result);
        assertEquals("hola mundo", result.fullResponse());
        assertEquals(11, result.inputTokens());
        assertEquals(7, result.outputTokens());
        assertEquals(18.0, meterRegistry.counter("ai.nest.stream.tokens.total").count());
    }

    @Test
    void streamCompletion_streamException_countsErrorMetric() {
        mockRequestChain();
        when(requestBodySpec.exchange(any()))
            .thenThrow(new AiStreamException("stream failed"));

        assertThrows(AiStreamException.class,
                () -> service.streamCompletion(request(), new SseEmitter(5000L), "jwt-token"));

        verify(circuitBreaker).onError(anyLong(), any(), any(Throwable.class));
    }

    @Test
    void streamCompletion_connectionError_countsConnectionMetric() {
        mockRequestChain();
        when(requestBodySpec.exchange(any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThrows(AiStreamException.class,
                () -> service.streamCompletion(request(), new SseEmitter(1000L), "jwt-token"));

        assertEquals(1.0, meterRegistry.counter("ai.nest.stream.errors.total", "type", "connection").count());
    }

    private NestCompletionRequest request() {
        return new NestCompletionRequest(
                "compressed-context",
                "sk-test",
                "OPENAI",
                "Admin",
                "es",
                "gpt-4o"
        );
    }

    private void mockRequestChain() {
        when(nestRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.accept(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(NestCompletionRequest.class))).thenReturn(requestBodySpec);
    }
}
