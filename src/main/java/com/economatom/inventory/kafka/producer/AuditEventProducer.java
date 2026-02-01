package com.economatom.inventory.kafka.producer;

import com.economatom.inventory.dto.event.InventoryAuditEvent;
import com.economatom.inventory.dto.event.OrderAuditEvent;
import com.economatom.inventory.dto.event.RecipeAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Productor de eventos de auditoría hacia Kafka.
 * Envía mensajes de forma asíncrona y no bloqueante.
 */
@Slf4j
@Service
@Profile("!test")
public class AuditEventProducer {

    private static final String INVENTORY_AUDIT_TOPIC = "inventory-audit-events";
    private static final String RECIPE_AUDIT_TOPIC = "recipe-audit-events";
    private static final String ORDER_AUDIT_TOPIC = "order-audit-events";

    private final KafkaTemplate<String, InventoryAuditEvent> inventoryKafkaTemplate;
    private final KafkaTemplate<String, RecipeAuditEvent> recipeKafkaTemplate;
    private final KafkaTemplate<String, OrderAuditEvent> orderKafkaTemplate;

    public AuditEventProducer(
            KafkaTemplate<String, InventoryAuditEvent> inventoryKafkaTemplate,
            KafkaTemplate<String, RecipeAuditEvent> recipeKafkaTemplate,
            KafkaTemplate<String, OrderAuditEvent> orderKafkaTemplate) {
        this.inventoryKafkaTemplate = inventoryKafkaTemplate;
        this.recipeKafkaTemplate = recipeKafkaTemplate;
        this.orderKafkaTemplate = orderKafkaTemplate;
    }

    /**
     * Publica un evento de auditoría de inventario en Kafka de forma asíncrona.
     */
    public void publishInventoryAudit(InventoryAuditEvent event) {
        try {
            String key = "product-" + event.getProductId();
            
            CompletableFuture<SendResult<String, InventoryAuditEvent>> future = 
                inventoryKafkaTemplate.send(INVENTORY_AUDIT_TOPIC, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Evento de auditoría de inventario enviado: producto={}, tipo={}, partition={}, offset={}", 
                        event.getProductId(), 
                        event.getMovementType(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Error al enviar evento de auditoría de inventario: producto={}, error={}", 
                        event.getProductId(), ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Excepción al publicar evento de auditoría de inventario: {}", e.getMessage(), e);
        }
    }

    /**
     * Publica un evento de auditoría de receta en Kafka de forma asíncrona.
     */
    public void publishRecipeAudit(RecipeAuditEvent event) {
        try {
            String key = "recipe-" + event.getRecipeId();
            
            CompletableFuture<SendResult<String, RecipeAuditEvent>> future = 
                recipeKafkaTemplate.send(RECIPE_AUDIT_TOPIC, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Evento de auditoría de receta enviado: receta={}, acción={}, partition={}, offset={}", 
                        event.getRecipeId(), 
                        event.getAction(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Error al enviar evento de auditoría de receta: receta={}, error={}", 
                        event.getRecipeId(), ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Excepción al publicar evento de auditoría de receta: {}", e.getMessage(), e);
        }
    }

    /**
     * Publica un evento de auditoría de orden en Kafka de forma asíncrona.
     */
    public void publishOrderAudit(OrderAuditEvent event) {
        try {
            String key = "order-" + event.getOrderId();
            
            CompletableFuture<SendResult<String, OrderAuditEvent>> future = 
                orderKafkaTemplate.send(ORDER_AUDIT_TOPIC, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Evento de auditoría de orden enviado: orden={}, acción={}, partition={}, offset={}", 
                        event.getOrderId(), 
                        event.getAction(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Error al enviar evento de auditoría de orden: orden={}, error={}", 
                        event.getOrderId(), ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Excepción al publicar evento de auditoría de orden: {}", e.getMessage(), e);
        }
    }
}
