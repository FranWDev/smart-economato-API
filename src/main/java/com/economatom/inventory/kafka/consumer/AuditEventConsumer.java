package com.economatom.inventory.kafka.consumer;

import com.economatom.inventory.dto.event.InventoryAuditEvent;
import com.economatom.inventory.dto.event.OrderAuditEvent;
import com.economatom.inventory.dto.event.RecipeAuditEvent;
import com.economatom.inventory.model.InventoryAudit;
import com.economatom.inventory.model.Order;
import com.economatom.inventory.model.OrderAudit;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.model.Recipe;
import com.economatom.inventory.model.RecipeAudit;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.InventoryAuditRepository;
import com.economatom.inventory.repository.OrderAuditRepository;
import com.economatom.inventory.repository.OrderRepository;
import com.economatom.inventory.repository.ProductRepository;
import com.economatom.inventory.repository.RecipeAuditRepository;
import com.economatom.inventory.repository.RecipeRepository;
import com.economatom.inventory.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de eventos de auditoría desde Kafka.
 * Procesa eventos de forma asíncrona y los persiste en la base de datos.
 */
@Slf4j
@Service
@Profile("!test")
public class AuditEventConsumer {

    private final InventoryAuditRepository inventoryAuditRepository;
    private final RecipeAuditRepository recipeAuditRepository;
    private final OrderAuditRepository orderAuditRepository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public AuditEventConsumer(
            InventoryAuditRepository inventoryAuditRepository,
            RecipeAuditRepository recipeAuditRepository,
            OrderAuditRepository orderAuditRepository,
            ProductRepository productRepository,
            RecipeRepository recipeRepository,
            OrderRepository orderRepository,
            UserRepository userRepository) {
        this.inventoryAuditRepository = inventoryAuditRepository;
        this.recipeAuditRepository = recipeAuditRepository;
        this.orderAuditRepository = orderAuditRepository;
        this.productRepository = productRepository;
        this.recipeRepository = recipeRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    /**
     * Consume eventos de auditoría de inventario y los persiste.
     */
    @KafkaListener(
        topics = "inventory-audit-events",
        groupId = "inventory-audit-consumer-group",
        containerFactory = "inventoryAuditKafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeInventoryAudit(InventoryAuditEvent event) {
        try {
            log.debug("Procesando evento de auditoría de inventario: producto={}, tipo={}", 
                event.getProductId(), event.getMovementType());

            Product product = productRepository.findById(event.getProductId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + event.getProductId()));

            User user = null;
            if (event.getUserId() != null) {
                user = userRepository.findById(event.getUserId()).orElse(null);
            }

            InventoryAudit audit = new InventoryAudit();
            audit.setProduct(product);
            audit.setUsers(user);
            audit.setMovementType(event.getMovementType());
            audit.setQuantity(event.getQuantity());
            audit.setActionDescription(event.getActionDescription());
            audit.setPreviousState(event.getPreviousState());
            audit.setNewState(event.getNewState());
            audit.setMovementDate(event.getMovementDate());

            inventoryAuditRepository.save(audit);

            log.info("Auditoría de inventario guardada: id={}, producto={}, tipo={}", 
                audit.getId(), event.getProductId(), event.getMovementType());

        } catch (Exception e) {
            log.error("Error al procesar evento de auditoría de inventario: {}", e.getMessage(), e);
            // Kafka reintentará automáticamente según configuración
            throw new RuntimeException("Error procesando auditoría de inventario", e);
        }
    }

    /**
     * Consume eventos de auditoría de receta y los persiste.
     */
    @KafkaListener(
        topics = "recipe-audit-events",
        groupId = "recipe-audit-consumer-group",
        containerFactory = "recipeAuditKafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeRecipeAudit(RecipeAuditEvent event) {
        try {
            log.debug("Procesando evento de auditoría de receta: receta={}, acción={}", 
                event.getRecipeId(), event.getAction());

            Recipe recipe = recipeRepository.findById(event.getRecipeId())
                .orElseThrow(() -> new RuntimeException("Receta no encontrada: " + event.getRecipeId()));

            User user = null;
            if (event.getUserId() != null) {
                user = userRepository.findById(event.getUserId()).orElse(null);
            }

            RecipeAudit audit = new RecipeAudit();
            audit.setRecipe(recipe);
            audit.setUsers(user);
            audit.setAction(event.getAction());
            audit.setDetails(event.getDetails());
            audit.setPreviousState(event.getPreviousState());
            audit.setNewState(event.getNewState());
            audit.setAuditDate(event.getAuditDate());

            recipeAuditRepository.save(audit);

            log.info("Auditoría de receta guardada: id={}, receta={}, acción={}", 
                audit.getId(), event.getRecipeId(), event.getAction());

        } catch (Exception e) {
            log.error("Error al procesar evento de auditoría de receta: {}", e.getMessage(), e);
            // Kafka reintentará automáticamente según configuración
            throw new RuntimeException("Error procesando auditoría de receta", e);
        }
    }

    /**
     * Consume eventos de auditoría de orden y los persiste.
     */
    @KafkaListener(
        topics = "order-audit-events",
        groupId = "order-audit-consumer-group",
        containerFactory = "orderAuditKafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeOrderAudit(OrderAuditEvent event) {
        try {
            log.debug("Procesando evento de auditoría de orden: orden={}, acción={}", 
                event.getOrderId(), event.getAction());

            Order order = orderRepository.findById(event.getOrderId())
                .orElse(null); // Puede ser null si la orden fue eliminada

            User user = null;
            if (event.getUserId() != null) {
                user = userRepository.findById(event.getUserId()).orElse(null);
            }

            OrderAudit audit = new OrderAudit();
            audit.setOrder(order);
            audit.setUsers(user);
            audit.setAction(event.getAction());
            audit.setDetails(event.getDetails());
            audit.setPreviousState(event.getPreviousState());
            audit.setNewState(event.getNewState());
            audit.setAuditDate(event.getAuditDate());

            orderAuditRepository.save(audit);

            log.info("Auditoría de orden guardada: id={}, orden={}, acción={}", 
                audit.getId(), event.getOrderId(), event.getAction());

        } catch (Exception e) {
            log.error("Error al procesar evento de auditoría de orden: {}", e.getMessage(), e);
            // Kafka reintentará automáticamente según configuración
            throw new RuntimeException("Error procesando auditoría de orden", e);
        }
    }
}
