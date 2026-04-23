package com.economato.inventory.application.usecase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.event.AiAuditEvent;
import com.economato.inventory.application.dto.mcp.NestCompletionRequest;
import com.economato.inventory.application.dto.mcp.NestStreamEvent;
import com.economato.inventory.application.dto.mcp.ToolCallInfo;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiStreamException;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.config.ai.AiNestProperties;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NestStreamBridgeService {

    private static final String EVENT_TOKEN = "token";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";
    private static final String EVENT_TOOL = "tool";
    private static final String EVENT_TOOL_CALLED = "tool_called";
    private static final String EVENT_THINKING = "thinking";
    private static final String EVENT_THINKING_DELTA = "thinking_delta";
    private static final String EVENT_TOOL_RESULT = "tool_result";

    @Qualifier("nestRestClient")
    private final RestClient nestRestClient;
    private final AiNestProperties aiNestProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Optional<AuditEventProducer> auditEventProducer;
    private final I18nService i18nService;

    public StreamCompletionResult streamCompletion(NestCompletionRequest request,
                                                   SseEmitter emitter,
                                                   String userJwt) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("nest");
        log.info("AI stream started: provider={}, user={}", request.provider(), request.userName());

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("Nest circuit breaker OPEN: provider={}, user={}", request.provider(), request.userName());
            counter("ai.nest.stream.errors.total", "type", "circuit_open").increment();
            throw new AiStreamException(i18nService.getMessage(MessageKey.ERROR_AI_STREAM_UNAVAILABLE));
        }

        long startNanos = System.nanoTime();
        try {
            StreamCompletionResult result = nestRestClient.post()
                    .uri(aiNestProperties.getCompletionEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .headers(headers -> applyHeaders(headers, userJwt))
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> handleResponse(clientResponse, emitter, request));

            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            circuitBreaker.onSuccess(durationMs, TimeUnit.MILLISECONDS);
            timerSample.stop(timer("ai.nest.stream.duration"));

            int totalTokens = safeInt(result.inputTokens()) + safeInt(result.outputTokens());
            counter("ai.nest.stream.tokens.total").increment(totalTokens);
            return result;
        } catch (AiStreamException ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            circuitBreaker.onError(durationMs, TimeUnit.MILLISECONDS, resolveRootCause(ex));
            counter("ai.nest.stream.errors.total", "type", resolveNestErrorType(ex)).increment();
            timerSample.stop(timer("ai.nest.stream.duration"));
            throw ex;
        } catch (Exception ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            circuitBreaker.onError(durationMs, TimeUnit.MILLISECONDS, resolveRootCause(ex));
            counter("ai.nest.stream.errors.total", "type", "connection").increment();
            timerSample.stop(timer("ai.nest.stream.duration"));
            throw new AiStreamException(i18nService.getMessage(MessageKey.ERROR_AI_STREAM_FAILED), ex);
        }
    }

    private StreamCompletionResult handleResponse(ClientHttpResponse response,
                                                  SseEmitter emitter,
                                                  NestCompletionRequest request) {
        try {
            if (response.getStatusCode().isError()) {
                throw new AiStreamException(i18nService.getMessage(MessageKey.ERROR_AI_STREAM_HTTP_ERROR, response.getStatusCode().value()));
            }

            String currentEvent = null;
            StringBuilder dataBuffer = new StringBuilder();
            String fullResponse = null;
            Integer inputTokens = null;
            Integer outputTokens = null;
            StringBuilder thinkingBuffer = new StringBuilder();
            List<ToolCallInfo> toolCalls = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (dataBuffer.length() > 0) {
                            dataBuffer.append('\n');
                        }
                        String rawData = line.substring(5);
                        if (rawData.startsWith(" ")) {
                            rawData = rawData.substring(1);
                        }
                        dataBuffer.append(rawData);
                    } else if (line.isBlank()) {
                        String eventType = (currentEvent != null && !currentEvent.isBlank()) ? currentEvent : EVENT_TOKEN;
                        
                        if (currentEvent != null || dataBuffer.length() > 0) {
                            String rawDataStr = dataBuffer.toString();
                            NestStreamEvent event = parseEvent(eventType, rawDataStr);
                            
                            if (EVENT_TOKEN.equals(event.type())) {
                                // Important: Forward the raw data received from upstream to avoid double-encoding 
                                // or stripping issues that break the client.
                                emitter.send(SseEmitter.event().name(EVENT_TOKEN).data(rawDataStr, MediaType.TEXT_PLAIN));
                            } else if (EVENT_DONE.equals(event.type())) {
                                fullResponse = event.fullResponse();
                                inputTokens = event.inputTokens();
                                outputTokens = event.outputTokens();
                                emitter.send(SseEmitter.event().name(EVENT_DONE).data("", MediaType.TEXT_PLAIN));
                                emitter.complete();
                            } else if (EVENT_TOOL.equals(event.type()) || EVENT_TOOL_CALLED.equals(event.type())) {
                                String toolName = event.data() != null ? event.data() : "unknown";
                                publishAudit(AiAuditEvent.builder()
                                        .eventType("AI_TOOL_CALLED")
                                        .userName(request.userName())
                                        .provider(request.provider())
                                        .userLanguage(request.userLanguage())
                                        .toolName(toolName)
                                        .eventTimestamp(LocalDateTime.now())
                                        .build());
                                log.debug("Nest tool call received: tool={}, provider={}", toolName, request.provider());
                                emitter.send(SseEmitter.event().name("tool_called")
                                        .data(objectMapper.writeValueAsString(java.util.Map.of("toolName", toolName)), MediaType.APPLICATION_JSON));
                                toolCalls.add(new ToolCallInfo(toolName, null, null));
                            } else if (EVENT_THINKING.equals(event.type()) || EVENT_THINKING_DELTA.equals(event.type())) {
                                // Preserve DB accumulation but forward raw payload
                                thinkingBuffer.append(event.data() != null ? event.data() : "");
                                emitter.send(SseEmitter.event().name("thinking").data(rawDataStr, MediaType.TEXT_PLAIN));
                                log.debug("Nest thinking received: provider={}", request.provider());
                                counter("ai.nest.stream.thinking.events").increment();
                            } else if (EVENT_TOOL_RESULT.equals(event.type())) {
                                emitter.send(SseEmitter.event().name("tool_result").data(rawDataStr, MediaType.TEXT_PLAIN));
                                if (!toolCalls.isEmpty()) {
                                    int lastIndex = toolCalls.size() - 1;
                                    ToolCallInfo lastTool = toolCalls.get(lastIndex);
                                    toolCalls.set(lastIndex, new ToolCallInfo(lastTool.toolName(), lastTool.toolCallId(), event.data()));
                                }
                            } else if (EVENT_ERROR.equals(event.type())) {
                                String message = event.data() != null ? event.data() : i18nService.getMessage(MessageKey.ERROR_AI_STREAM_UNKNOWN);
                                emitter.send(SseEmitter.event().name(EVENT_ERROR).data(message, MediaType.TEXT_PLAIN));
                                emitter.completeWithError(new AiStreamException(message));
                                throw new AiStreamException(message);
                            }
                        }
                        currentEvent = null;
                        dataBuffer.setLength(0);
                    }
                }
            }

            return new StreamCompletionResult(fullResponse, inputTokens, outputTokens, thinkingBuffer.toString(), toolCalls);
        } catch (AiStreamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiStreamException(i18nService.getMessage(MessageKey.ERROR_AI_STREAM_PROCESSING_ERROR), ex);
        }
    }

    private NestStreamEvent parseEvent(String eventType, String rawData) {
        try {
            if (rawData != null && rawData.startsWith("{")) {
                NestStreamEvent parsed = objectMapper.readValue(rawData, NestStreamEvent.class);
                if (parsed.type() != null) {
                    return parsed;
                }
                return new NestStreamEvent(eventType, parsed.data(), parsed.fullResponse(), parsed.thinkingContent(), parsed.inputTokens(), parsed.outputTokens());
            }
        } catch (Exception ex) {
            log.debug("Unable to parse Nest SSE JSON payload, using raw fallback: {}", ex.getMessage());
        }
        return new NestStreamEvent(eventType, rawData, null, null, null, null);
    }

    private void applyHeaders(HttpHeaders headers, String userJwt) {
        if (userJwt != null && !userJwt.isBlank()) {
            headers.setBearerAuth(userJwt);
        }
        headers.set(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private Counter counter(String name, String... tags) {
        return meterRegistry.counter(name, tags);
    }

    private Timer timer(String name) {
        return meterRegistry.timer(name);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Throwable resolveRootCause(Throwable exception) {
        Throwable current = exception;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current != null ? current : exception;
    }

    private String resolveNestErrorType(Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("timeout")) {
            return "timeout";
        }
        if (message.contains("connect") || message.contains("connection")) {
            return "connection";
        }
        return "connection";
    }

    private void publishAudit(AiAuditEvent event) {
        auditEventProducer.ifPresent(producer -> producer.publishAiAudit(event));
    }

    public record StreamCompletionResult(
            String fullResponse,
            Integer inputTokens,
            Integer outputTokens,
            String thinkingContent,
            List<ToolCallInfo> toolCalls
    ) {
    }
}
