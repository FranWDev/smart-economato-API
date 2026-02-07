package com.economatom.inventory.kafka.producer;

import com.economatom.inventory.dto.event.InventoryAuditEvent;
import com.economatom.inventory.dto.event.OrderAuditEvent;
import com.economatom.inventory.dto.event.RecipeAuditEvent;
import com.economatom.inventory.dto.event.RecipeCookingAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@Profile("!test")
public class AuditEventProducer {

    private static final String INVENTORY_AUDIT_TOPIC = "inventory-audit-events";
    private static final String RECIPE_AUDIT_TOPIC = "recipe-audit-events";
    private static final String ORDER_AUDIT_TOPIC = "order-audit-events";
    private static final String RECIPE_COOKING_AUDIT_TOPIC = "recipe-cooking-audit-events";

    private final KafkaTemplate<String, InventoryAuditEvent> inventoryKafkaTemplate;
    private final KafkaTemplate<String, RecipeAuditEvent> recipeKafkaTemplate;
    private final KafkaTemplate<String, OrderAuditEvent> orderKafkaTemplate;
    private final KafkaTemplate<String, RecipeCookingAuditEvent> recipeCookingKafkaTemplate;

    public AuditEventProducer(
            KafkaTemplate<String, InventoryAuditEvent> inventoryKafkaTemplate,
            KafkaTemplate<String, RecipeAuditEvent> recipeKafkaTemplate,
            KafkaTemplate<String, OrderAuditEvent> orderKafkaTemplate,
            KafkaTemplate<String, RecipeCookingAuditEvent> recipeCookingKafkaTemplate) {
        this.inventoryKafkaTemplate = inventoryKafkaTemplate;
        this.recipeKafkaTemplate = recipeKafkaTemplate;
        this.orderKafkaTemplate = orderKafkaTemplate;
        this.recipeCookingKafkaTemplate = recipeCookingKafkaTemplate;
    }

    public void publishInventoryAudit(InventoryAuditEvent event) {
        try {
            String key = "product-" + event.getProductId();

            CompletableFuture<SendResult<String, InventoryAuditEvent>> future = inventoryKafkaTemplate
                    .send(INVENTORY_AUDIT_TOPIC, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug(
                            "Evento de auditoría de inventario enviado: producto={}, tipo={}, partition={}, offset={}",
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

    public void publishRecipeAudit(RecipeAuditEvent event) {
        try {
            String key = "recipe-" + event.getRecipeId();

            CompletableFuture<SendResult<String, RecipeAuditEvent>> future = recipeKafkaTemplate
                    .send(RECIPE_AUDIT_TOPIC, key, event);

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

    public void publishOrderAudit(OrderAuditEvent event) {
        try {
            String key = "order-" + event.getOrderId();

            CompletableFuture<SendResult<String, OrderAuditEvent>> future = orderKafkaTemplate.send(ORDER_AUDIT_TOPIC,
                    key, event);

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

    public void publishRecipeCookingAudit(RecipeCookingAuditEvent event) {
        try {
            String key = "recipe-cooking-" + event.getRecipeId();

            CompletableFuture<SendResult<String, RecipeCookingAuditEvent>> future = recipeCookingKafkaTemplate
                    .send(RECIPE_COOKING_AUDIT_TOPIC, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug(
                            "Evento de auditoría de cocinado enviado: receta={}, cantidad={}, partition={}, offset={}",
                            event.getRecipeId(),
                            event.getQuantityCooked(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("Error al enviar evento de auditoría de cocinado: receta={}, error={}",
                            event.getRecipeId(), ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Excepción al publicar evento de auditoría de cocinado: {}", e.getMessage(), e);
        }
    }
}
