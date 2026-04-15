package com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer;

import com.economato.inventory.application.dto.event.InventoryAuditEvent;
import com.economato.inventory.application.dto.event.AiAuditEvent;
import com.economato.inventory.application.dto.event.BlockchainAuditEvent;
import com.economato.inventory.application.dto.event.OrderAuditEvent;
import com.economato.inventory.application.dto.event.PresenceAuditEvent;
import com.economato.inventory.application.dto.event.RecipeAuditEvent;
import com.economato.inventory.application.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.application.dto.event.StockPredictionEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;


@Service
@Profile({ "!test", "kafka-test" })
public class AuditEventProducer {

    public static final String INVENTORY_AUDIT_TOPIC = "inventory-audit-events";
    public static final String RECIPE_AUDIT_TOPIC = "recipe-audit-events";
    public static final String ORDER_AUDIT_TOPIC = "order-audit-events";
    public static final String RECIPE_COOKING_AUDIT_TOPIC = "recipe-cooking-audit-events";
    public static final String STOCK_PREDICTION_TOPIC = "stock-prediction-events";
    public static final String PRESENCE_AUDIT_TOPIC = "presence-audit-events";
    public static final String LEDGER_BLOCK_TOPIC = "ledger-block-events";
    public static final String AI_AUDIT_TOPIC = "ai-audit-events";

    private final AuditOutboxWriter auditOutboxWriter;

    public AuditEventProducer(AuditOutboxWriter auditOutboxWriter) {
        this.auditOutboxWriter = auditOutboxWriter;
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

    public void publishStockPredictionEvent(StockPredictionEvent event) {
        saveToOutbox(STOCK_PREDICTION_TOPIC, "prediction-" + event.getTriggerType(), event);
    }

    public void publishPresenceAudit(PresenceAuditEvent event) {
        saveToOutbox(PRESENCE_AUDIT_TOPIC, "user-" + event.getUserId(), event);
    }

    public void publishBlockchainEvent(BlockchainAuditEvent event) {
        saveToOutbox(LEDGER_BLOCK_TOPIC, "block-" + event.getBlockNumber(), event);
    }

    public void publishAiAudit(AiAuditEvent event) {
        String key = event.getUserId() == null ? "ai-user-anonymous" : "ai-user-" + event.getUserId();
        saveToOutbox(AI_AUDIT_TOPIC, key, event);
    }

    private void saveToOutbox(String topic, String key, Object event) {
        auditOutboxWriter.saveToOutbox(topic, key, event);
    }
}
