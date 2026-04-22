package com.economato.inventory.application.usecase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.mcp.NestCompletionRequest;
import com.economato.inventory.application.dto.mcp.ToolCallInfo;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiStreamException;
import com.economato.inventory.infrastructure.config.ai.AiNestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.economato.inventory.infrastructure.config.web.I18nService;

@ExtendWith(MockitoExtension.class)
class NestStreamBridgeServiceTest {

    @Mock
    private RestClient nestRestClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private I18nService i18nService;
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

        service = new NestStreamBridgeService(
                nestRestClient,
                props,
                circuitBreakerRegistry,
                meterRegistry,
                new ObjectMapper(),
                Optional.empty(),
                i18nService
        );
    }

    @Test
    void streamCompletion_circuitBreakerOpen_throwsImmediately() {
                when(circuitBreakerRegistry.circuitBreaker("nest")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        assertThrows(AiStreamException.class,
                () -> service.streamCompletion(request(), new SseEmitter(1000L), "jwt-token"));
    }

    @Test
    void streamCompletion_successfulStream_returnsTokensAndText() throws Exception {
                mockCircuitBreakerClosed();
        mockRequestChain();
        when(requestBodySpec.exchange(any()))
                .thenReturn(new NestStreamBridgeService.StreamCompletionResult("hola mundo", 11, 7, null, new ArrayList<ToolCallInfo>()));

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
                mockCircuitBreakerClosed();
        mockRequestChain();
        when(requestBodySpec.exchange(any()))
            .thenThrow(new AiStreamException("stream failed"));

        assertThrows(AiStreamException.class,
                () -> service.streamCompletion(request(), new SseEmitter(5000L), "jwt-token"));

        verify(circuitBreaker).onError(anyLong(), any(), any(Throwable.class));
    }

    @Test
    void streamCompletion_connectionError_countsConnectionMetric() {
                mockCircuitBreakerClosed();
        mockRequestChain();
        when(requestBodySpec.exchange(any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThrows(AiStreamException.class,
                () -> service.streamCompletion(request(), new SseEmitter(1000L), "jwt-token"));

        assertEquals(1.0, meterRegistry.counter("ai.nest.stream.errors.total", "type", "connection").count());
    }

    @Test
    void streamCompletion_sseParser_preservesTokenSpaces() throws Exception {
        mockCircuitBreakerClosed();
        mockRequestChain();

        String ssePayload = """
                event:token
                data: Hello

                event:token
                data:  world

                event:done
                data:{\"fullResponse\":\"Hello world\",\"inputTokens\":5,\"outputTokens\":3}

                """;

        when(requestBodySpec.exchange(any())).thenAnswer(invocation -> {
            RestClient.RequestHeadersSpec.ExchangeFunction<?> exchangeFunction =
                    invocation.getArgument(0, RestClient.RequestHeadersSpec.ExchangeFunction.class);
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse clientHttpResponse =
                    mock(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse.class);
            when(clientHttpResponse.getStatusCode()).thenReturn(HttpStatus.OK);
            when(clientHttpResponse.getBody())
                    .thenReturn(new ByteArrayInputStream(ssePayload.getBytes(StandardCharsets.UTF_8)));
            return exchangeFunction.exchange(null, clientHttpResponse);
        });

        CapturingSseEmitter emitter = new CapturingSseEmitter();
        NestStreamBridgeService.StreamCompletionResult result =
                service.streamCompletion(request(), emitter, "jwt-token");

        List<String> emittedTokens = emitter.nonEmptyPayloads().stream()
                .filter(payload -> payload.startsWith("event:token"))
                .map(CapturingSseEmitter::extractDataPayload)
                .toList();

        assertEquals(List.of("Hello", " world"), emittedTokens);
        assertEquals("Hello world", result.fullResponse());
        assertEquals(5, result.inputTokens());
        assertEquals(3, result.outputTokens());
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

        private void mockCircuitBreakerClosed() {
                when(circuitBreakerRegistry.circuitBreaker("nest")).thenReturn(circuitBreaker);
                when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        }

        private static class CapturingSseEmitter extends SseEmitter {
                private final List<String> payloads = new ArrayList<>();

                @Override
                public synchronized void send(SseEventBuilder builder) throws IOException {
                        payloads.add(readPayload(builder));
                }

                private List<String> nonEmptyPayloads() {
                        return payloads.stream()
                                        .filter(payload -> payload != null && !payload.isEmpty())
                                        .toList();
                }

                private static String extractDataPayload(String serializedEvent) {
                        for (String line : serializedEvent.split("\\R")) {
                                if (line.startsWith("data:")) {
                                        return line.substring(5);
                                }
                        }
                        return "";
                }

                private String readPayload(SseEventBuilder builder) {
                        try {
                                StringBuilder payload = new StringBuilder();

                                // Read event name
                                Field nameField = findField(builder.getClass(), "name");
                                String name = (String) (nameField != null ? readFieldValue(nameField, builder) : null);
                                if (name != null) {
                                        payload.append("event:").append(name).append("\n");
                                }

                                Field dataField = findField(builder.getClass(), "dataToSend");
                                if (dataField == null) {
                                        throw new IllegalStateException("Missing dataToSend field in SseEventBuilder");
                                }
                                @SuppressWarnings("unchecked")
                                LinkedHashSet<Object> dataToSend = (LinkedHashSet<Object>) readFieldValue(dataField, builder);

                                for (Object dataWithMediaType : dataToSend) {
                                        Field rawDataField = findField(dataWithMediaType.getClass(), "data");
                                        Object rawData = rawDataField != null ? readFieldValue(rawDataField, dataWithMediaType) : null;
                                        if (rawData != null) {
                                                payload.append("data:").append(rawData).append("\n");
                                        }
                                }

                                return payload.toString();
                        } catch (Exception ex) {
                                throw new IllegalStateException("Unable to capture SSE event", ex);
                        }
                }

                private Object readFieldValue(Field field, Object instance) throws IllegalAccessException {
                        field.setAccessible(true);
                        return field.get(instance);
                }

                private Field findField(Class<?> type, String... fieldNames) {
                        Class<?> current = type;
                        while (current != null) {
                                for (String fieldName : fieldNames) {
                                        try {
                                                return current.getDeclaredField(fieldName);
                                        } catch (NoSuchFieldException ignored) {
                                                // Keep searching in the class hierarchy.
                                        }
                                }
                                current = current.getSuperclass();
                        }
                        return null;
                }
        }

}
