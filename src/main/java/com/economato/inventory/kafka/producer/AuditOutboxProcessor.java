package com.economato.inventory.kafka.producer;

import com.economato.inventory.dto.event.InventoryAuditEvent;
import com.economato.inventory.dto.event.OrderAuditEvent;
import com.economato.inventory.dto.event.RecipeAuditEvent;
import com.economato.inventory.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.model.AuditOutbox;
import com.economato.inventory.repository.AuditOutboxRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    public AuditOutboxProcessor(
            AuditOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            KafkaTemplate<String, InventoryAuditEvent> inventoryKafkaTemplate,
            KafkaTemplate<String, RecipeAuditEvent> recipeKafkaTemplate,
            KafkaTemplate<String, OrderAuditEvent> orderKafkaTemplate,
            KafkaTemplate<String, RecipeCookingAuditEvent> recipeCookingKafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.inventoryKafkaTemplate = inventoryKafkaTemplate;
        this.recipeKafkaTemplate = recipeKafkaTemplate;
        this.orderKafkaTemplate = orderKafkaTemplate;
        this.recipeCookingKafkaTemplate = recipeCookingKafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        List<AuditOutbox> outboxEvents = outboxRepository.findTop100ByOrderByCreatedAtAsc();

        for (AuditOutbox event : outboxEvents) {
            try {
                CompletableFuture<?> future = null;

                switch (event.getTopic()) {
                    case AuditEventProducer.INVENTORY_AUDIT_TOPIC:
                        InventoryAuditEvent invEvent = objectMapper.readValue(event.getPayload(),
                                InventoryAuditEvent.class);
                        future = inventoryKafkaTemplate.send(event.getTopic(), event.getEventKey(), invEvent);
                        break;
                    case AuditEventProducer.RECIPE_AUDIT_TOPIC:
                        RecipeAuditEvent recEvent = objectMapper.readValue(event.getPayload(), RecipeAuditEvent.class);
                        future = recipeKafkaTemplate.send(event.getTopic(), event.getEventKey(), recEvent);
                        break;
                    case AuditEventProducer.ORDER_AUDIT_TOPIC:
                        OrderAuditEvent ordEvent = objectMapper.readValue(event.getPayload(), OrderAuditEvent.class);
                        future = orderKafkaTemplate.send(event.getTopic(), event.getEventKey(), ordEvent);
                        break;
                    case AuditEventProducer.RECIPE_COOKING_AUDIT_TOPIC:
                        RecipeCookingAuditEvent recCookEvent = objectMapper.readValue(event.getPayload(),
                                RecipeCookingAuditEvent.class);
                        future = recipeCookingKafkaTemplate.send(event.getTopic(), event.getEventKey(), recCookEvent);
                        break;
                    default:
                        log.warn("Topic no reconocido en Outbox: {}", event.getTopic());
                        outboxRepository.delete(event); // Eliminar eventos inválidos
                        continue;
                }

                if (future != null) {
                    future.whenComplete((result, ex) -> {
                        if (ex == null) {
                            outboxRepository.delete(event);
                            log.debug("Evento de Outbox enviado a Kafka con éxito: topic={}, key={}", event.getTopic(),
                                    event.getEventKey());
                        } else {
                            log.error("Error enviando evento de Outbox a Kafka: topic={}, key={}, error={}",
                                    event.getTopic(), event.getEventKey(), ex.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Error procesando evento Outbox: id={}, error={}", event.getId(), e.getMessage());
            }
        }
    }
}
