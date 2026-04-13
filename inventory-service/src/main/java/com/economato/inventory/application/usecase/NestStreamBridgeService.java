package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.mcp.NestCompletionRequest;
import com.economato.inventory.application.dto.mcp.NestStreamEvent;
import com.economato.inventory.infrastructure.adapter.in.web.AiStreamException;
import com.economato.inventory.infrastructure.config.ai.AiNestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NestStreamBridgeService {

    private static final String EVENT_TOKEN = "token";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";

    @Qualifier("nestRestClient")
    private final RestClient nestRestClient;
    private final AiNestProperties aiNestProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public StreamCompletionResult streamCompletion(NestCompletionRequest request,
                                                   SseEmitter emitter,
                                                   String userJwt) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("nest");

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            counter("ai.nest.stream.errors.total", "type", "circuit_open").increment();
            throw new AiStreamException("Nest stream is unavailable: circuit breaker is OPEN");
        }

        long startNanos = System.nanoTime();
        try {
            StreamCompletionResult result = nestRestClient.post()
                    .uri(aiNestProperties.getCompletionEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .headers(headers -> applyHeaders(headers, userJwt))
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> handleResponse(clientResponse, emitter));

            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            circuitBreaker.onSuccess(durationMs, TimeUnit.MILLISECONDS);
            timerSample.stop(timer("ai.nest.stream.duration"));

            int totalTokens = safeInt(result.inputTokens()) + safeInt(result.outputTokens());
            counter("ai.nest.stream.tokens.total").increment(totalTokens);
            return result;
        } catch (AiStreamException ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            circuitBreaker.onError(durationMs, TimeUnit.MILLISECONDS, resolveRootCause(ex));
            counter("ai.nest.stream.errors.total", "type", "stream_error").increment();
            timerSample.stop(timer("ai.nest.stream.duration"));
            throw ex;
        } catch (Exception ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            circuitBreaker.onError(durationMs, TimeUnit.MILLISECONDS, resolveRootCause(ex));
            counter("ai.nest.stream.errors.total", "type", "connection").increment();
            timerSample.stop(timer("ai.nest.stream.duration"));
            throw new AiStreamException("Failed to stream completion from Nest service", ex);
        }
    }

    private StreamCompletionResult handleResponse(ClientHttpResponse response, SseEmitter emitter) {
        try {
            if (response.getStatusCode().isError()) {
                throw new AiStreamException("Nest stream responded with HTTP " + response.getStatusCode().value());
            }

            String currentEvent = null;
            StringBuilder dataBuffer = new StringBuilder();
            String fullResponse = null;
            Integer inputTokens = null;
            Integer outputTokens = null;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (!dataBuffer.isEmpty()) {
                            dataBuffer.append('\n');
                        }
                        dataBuffer.append(line.substring(5).trim());
                    } else if (line.isBlank()) {
                        if (currentEvent != null) {
                            NestStreamEvent event = parseEvent(currentEvent, dataBuffer.toString());
                            if (EVENT_TOKEN.equals(event.type())) {
                                String token = event.data() != null ? event.data() : "";
                                emitter.send(SseEmitter.event().name(EVENT_TOKEN).data(token));
                            } else if (EVENT_DONE.equals(event.type())) {
                                fullResponse = event.fullResponse();
                                inputTokens = event.inputTokens();
                                outputTokens = event.outputTokens();
                                emitter.send(SseEmitter.event().name(EVENT_DONE).data(""));
                                emitter.complete();
                            } else if (EVENT_ERROR.equals(event.type())) {
                                String message = event.data() != null ? event.data() : "Unknown stream error";
                                emitter.send(SseEmitter.event().name(EVENT_ERROR).data(message));
                                emitter.completeWithError(new AiStreamException(message));
                                throw new AiStreamException(message);
                            }
                        }
                        currentEvent = null;
                        dataBuffer.setLength(0);
                    }
                }
            }

            return new StreamCompletionResult(fullResponse, inputTokens, outputTokens);
        } catch (AiStreamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiStreamException("Error while processing Nest SSE stream", ex);
        }
    }

    private NestStreamEvent parseEvent(String eventType, String rawData) {
        try {
            if (rawData != null && rawData.startsWith("{")) {
                NestStreamEvent parsed = objectMapper.readValue(rawData, NestStreamEvent.class);
                if (parsed.type() != null) {
                    return parsed;
                }
                return new NestStreamEvent(eventType, parsed.data(), parsed.fullResponse(), parsed.inputTokens(), parsed.outputTokens());
            }
        } catch (Exception ex) {
            log.debug("Unable to parse Nest SSE JSON payload, using raw fallback: {}", ex.getMessage());
        }
        return new NestStreamEvent(eventType, rawData, null, null, null);
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

    public record StreamCompletionResult(
            String fullResponse,
            Integer inputTokens,
            Integer outputTokens
    ) {
    }
}
