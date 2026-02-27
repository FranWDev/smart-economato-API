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
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({ "!test", "kafka-test" })
public class AuditEventProducer {

    public static final String INVENTORY_AUDIT_TOPIC = "inventory-audit-events";
    public static final String RECIPE_AUDIT_TOPIC = "recipe-audit-events";
    public static final String ORDER_AUDIT_TOPIC = "order-audit-events";
    public static final String RECIPE_COOKING_AUDIT_TOPIC = "recipe-cooking-audit-events";

    private final AuditOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AuditEventProducer(AuditOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishInventoryAudit(InventoryAuditEvent event) {
        saveToOutbox(INVENTORY_AUDIT_TOPIC, "product-" + event.getProductId(), event);
    }

    public void publishRecipeAudit(RecipeAuditEvent event) {
        saveToOutbox(RECIPE_AUDIT_TOPIC, "recipe-" + event.getRecipeId(), event);
    }

    public void publishOrderAudit(OrderAuditEvent event) {
        saveToOutbox(ORDER_AUDIT_TOPIC, "order-" + event.getOrderId(), event);
    }

    public void publishRecipeCookingAudit(RecipeCookingAuditEvent event) {
        saveToOutbox(RECIPE_COOKING_AUDIT_TOPIC, "recipe-cooking-" + event.getRecipeId(), event);
    }

    private void saveToOutbox(String topic, String key, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            AuditOutbox outbox = AuditOutbox.builder()
                    .topic(topic)
                    .eventKey(key)
                    .payload(payload)
                    .build();
            outboxRepository.save(outbox);
            log.debug("Evento de auditoría guardado en Outbox: topic={}, key={}", topic, key);
        } catch (Exception e) {
            log.error("Excepción al guardar evento en Outbox: {}", e.getMessage(), e);
        }
    }
}
