package com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.economato.inventory.application.dto.event.InventoryAuditEvent;
import com.economato.inventory.application.dto.event.OrderAuditEvent;
import com.economato.inventory.application.dto.event.RecipeAuditEvent;
import com.economato.inventory.application.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.domain.model.AuditOutbox;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AuditOutboxRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/*
 * Procesador de Outbox para eventos de auditoría.
 * Lee eventos pendientes de la tabla Outbox, los envía a Kafka y los marca como procesados eliminándolos de la tabla.
 * Implementa un mecanismo de detección de fallos en Kafka basado en contar fallos consecutivos y 
 * abrir un circuit breaker para evitar procesar eventos innecesariamente cuando Kafka está caído.
 * 
 * Vale si, muy bonita la explicacion, pero que es un Outbox? 
 * El patrón Outbox es una técnica para garantizar la consistencia entre la base de datos 
 * y los sistemas externos (como Kafka) en arquitecturas de microservicios. 
 * En lugar de enviar eventos directamente a Kafka dentro de la misma transacción que actualiza la base de datos,
 * el servicio escribe un registro en una tabla "Outbox" dentro de la misma transacción. Luego, un proceso separado (como este AuditOutboxProcessor) 
 * lee periódicamente los registros pendientes de la tabla Outbox, los envía a Kafka y los marca como procesados (generalmente eliminándolos). 
 * Esto asegura que no se pierdan eventos incluso si Kafka está temporalmente inalcanzable, 
 * ya que los eventos permanecen en la tabla Outbox hasta que se envían con éxito.
 */
@Slf4j
@Service
@Profile({ "!test", "kafka-test" })
public class AuditOutboxProcessor {

    private final AuditOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, InventoryAuditEvent> inventoryKafkaTemplate;
    private final KafkaTemplate<String, RecipeAuditEvent> recipeKafkaTemplate;
    private final KafkaTemplate<String, OrderAuditEvent> orderKafkaTemplate;
    private final KafkaTemplate<String, RecipeCookingAuditEvent> recipeCookingKafkaTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public AuditOutboxProcessor(
            AuditOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            KafkaTemplate<String, InventoryAuditEvent> inventoryKafkaTemplate,
            KafkaTemplate<String, RecipeAuditEvent> recipeKafkaTemplate,
            KafkaTemplate<String, OrderAuditEvent> orderKafkaTemplate,
            KafkaTemplate<String, RecipeCookingAuditEvent> recipeCookingKafkaTemplate,
            MeterRegistry meterRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.inventoryKafkaTemplate = inventoryKafkaTemplate;
        this.recipeKafkaTemplate = recipeKafkaTemplate;
        this.orderKafkaTemplate = orderKafkaTemplate;
        this.recipeCookingKafkaTemplate = recipeCookingKafkaTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;

        // Registrar Gauge para eventos pendientes en Outbox
        Gauge.builder("kafka.audit.outbox.pending",
                outboxRepository,
                AuditOutboxRepository::count)
                .description("Eventos pendientes en Outbox (Lag de Integración)")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        CircuitBreaker kafkaCb = circuitBreakerRegistry.circuitBreaker("kafka");
        if (kafkaCb.getState() == CircuitBreaker.State.OPEN) {
            log.debug("Kafka circuit breaker OPEN, skipping outbox processing");
            return;
        }

        List<AuditOutbox> outboxEvents;
        try {
            outboxEvents = outboxRepository.findTop50ByOrderByCreatedAtAsc();
        } catch (CallNotPermittedException e) {
            log.warn("DB circuit breaker OPEN, cannot read outbox: {}", e.getMessage());
            return;
        }

        int consecutiveKafkaFailures = 0;
        final int MAX_CONSECUTIVE_FAILURES = 3; // Umbral para detectar caída de Kafka y evitar procesar eventos
                                                // innecesariamente

        for (AuditOutbox event : outboxEvents) {
            try {
                Object auditEvent = null;
                try {
                    switch (event.getTopic()) {
                        case AuditEventProducer.INVENTORY_AUDIT_TOPIC:
                            auditEvent = objectMapper.readValue(event.getPayload(), InventoryAuditEvent.class);
                            break;
                        case AuditEventProducer.RECIPE_AUDIT_TOPIC:
                            auditEvent = objectMapper.readValue(event.getPayload(), RecipeAuditEvent.class);
                            break;
                        case AuditEventProducer.ORDER_AUDIT_TOPIC:
                            auditEvent = objectMapper.readValue(event.getPayload(), OrderAuditEvent.class);
                            break;
                        case AuditEventProducer.RECIPE_COOKING_AUDIT_TOPIC:
                            auditEvent = objectMapper.readValue(event.getPayload(), RecipeCookingAuditEvent.class);
                            break;
                        default:
                            log.warn("Topic no reconocido en Outbox: {}", event.getTopic());
                            outboxRepository.delete(event);
                            continue;
                    }
                } catch (CallNotPermittedException e) {
                    throw e; // Propagar para que el proceso se detenga si la db está inalcanzable
                } catch (Exception e) {
                    log.debug("Corrupted event payload in Outbox: id={}, topic={}, error={}",
                            event.getId(), event.getTopic(), e.getMessage());
                    try {
                        outboxRepository.delete(event);
                    } catch (CallNotPermittedException dbEx) {
                        throw dbEx;
                    } catch (Exception deleteEx) {
                        log.warn("Failed to delete corrupted event: {}", deleteEx.getMessage());
                    }
                    continue;
                }

                CompletableFuture<?> future = null;
                if (auditEvent != null) {
                    switch (event.getTopic()) {
                        case AuditEventProducer.INVENTORY_AUDIT_TOPIC:
                            future = inventoryKafkaTemplate.send(event.getTopic(), event.getEventKey(),
                                    (InventoryAuditEvent) auditEvent);
                            break;
                        case AuditEventProducer.RECIPE_AUDIT_TOPIC:
                            future = recipeKafkaTemplate.send(event.getTopic(), event.getEventKey(),
                                    (RecipeAuditEvent) auditEvent);
                            break;
                        case AuditEventProducer.ORDER_AUDIT_TOPIC:
                            future = orderKafkaTemplate.send(event.getTopic(), event.getEventKey(),
                                    (OrderAuditEvent) auditEvent);
                            break;
                        case AuditEventProducer.RECIPE_COOKING_AUDIT_TOPIC:
                            future = recipeCookingKafkaTemplate.send(event.getTopic(), event.getEventKey(),
                                    (RecipeCookingAuditEvent) auditEvent);
                            break;
                    }
                }

                if (future != null) {
                    try {
                        future.get(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Kafka send interrupted", e);
                    } catch (ExecutionException | TimeoutException e) {
                        throw e;
                    }

                    outboxRepository.delete(event);
                    log.debug("Evento de Outbox enviado a Kafka con éxito: topic={}, key={}", event.getTopic(),
                            event.getEventKey());

                    consecutiveKafkaFailures = 0;
                }
            } catch (ExecutionException | TimeoutException e) {
                log.error("Error procesando evento Outbox (Kafka): id={}, error={}", event.getId(), e.getMessage());
                recordKafkaFailure(e);

                consecutiveKafkaFailures++;
                if (consecutiveKafkaFailures >= MAX_CONSECUTIVE_FAILURES) {
                    log.warn(
                            "Detected {} consecutive Kafka failures. Kafka likely down. Breaking out of batch to fail fast.",
                            consecutiveKafkaFailures);
                    break;
                }
            } catch (CallNotPermittedException e) {
                log.warn("DB Circuit Breaker OPEN: Cannot read/write Outbox. id={}, reason: {}",
                        event.getId(), e.getMessage());
                break;
            } catch (Exception e) {
                log.error("Unexpected error processing event Outbox: id={}, error={}", event.getId(), e.getMessage());
            }
        }
    }

    private void recordKafkaFailure(Exception e) {
        try {
            var circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
            Throwable cause = (e instanceof ExecutionException && e.getCause() != null) ? e.getCause() : e;
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, cause);
        } catch (Exception ex) {
            log.warn("Failed to record Kafka failure in circuit breaker: {}", ex.getMessage());
        }
    }
}
