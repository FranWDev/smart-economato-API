package com.economato.inventory.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.event.InventoryAuditEvent;
import com.economato.inventory.dto.event.OrderAuditEvent;
import com.economato.inventory.dto.event.RecipeAuditEvent;
import com.economato.inventory.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.model.InventoryAudit;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.OrderAudit;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeAudit;
import com.economato.inventory.model.RecipeCookingAudit;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.InventoryAuditRepository;
import com.economato.inventory.repository.OrderAuditRepository;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeAuditRepository;
import com.economato.inventory.repository.RecipeCookingAuditRepository;
import com.economato.inventory.repository.RecipeRepository;
import com.economato.inventory.repository.UserRepository;

@Slf4j
@Service
@Profile({ "!test", "kafka-test" })
public class AuditEventConsumer {

    private final InventoryAuditRepository inventoryAuditRepository;
    private final RecipeAuditRepository recipeAuditRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final OrderAuditRepository orderAuditRepository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public AuditEventConsumer(
            InventoryAuditRepository inventoryAuditRepository,
            RecipeAuditRepository recipeAuditRepository,
            RecipeCookingAuditRepository recipeCookingAuditRepository,
            OrderAuditRepository orderAuditRepository,
            ProductRepository productRepository,
            RecipeRepository recipeRepository,
            OrderRepository orderRepository,
            UserRepository userRepository) {
        this.inventoryAuditRepository = inventoryAuditRepository;
        this.recipeAuditRepository = recipeAuditRepository;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
        this.orderAuditRepository = orderAuditRepository;
        this.productRepository = productRepository;
        this.recipeRepository = recipeRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @KafkaListener(topics = "inventory-audit-events", groupId = "inventory-audit-consumer-group", containerFactory = "inventoryAuditKafkaListenerContainerFactory")
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

            throw new RuntimeException("Error procesando auditoría de inventario", e);
        }
    }

    @KafkaListener(topics = "recipe-audit-events", groupId = "recipe-audit-consumer-group", containerFactory = "recipeAuditKafkaListenerContainerFactory")
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

            throw new RuntimeException("Error procesando auditoría de receta", e);
        }
    }

    @KafkaListener(topics = "order-audit-events", groupId = "order-audit-consumer-group", containerFactory = "orderAuditKafkaListenerContainerFactory")
    @Transactional
    public void consumeOrderAudit(OrderAuditEvent event) {
        try {
            log.debug("Procesando evento de auditoría de orden: orden={}, acción={}",
                    event.getOrderId(), event.getAction());

            Order order = orderRepository.findById(event.getOrderId())
                    .orElse(null);

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

            throw new RuntimeException("Error procesando auditoría de orden", e);
        }
    }

    @KafkaListener(topics = "recipe-cooking-audit-events", groupId = "recipe-cooking-audit-consumer-group", containerFactory = "recipeCookingAuditKafkaListenerContainerFactory")
    @Transactional
    public void consumeRecipeCookingAudit(RecipeCookingAuditEvent event) {
        try {
            log.debug("Procesando evento de auditoría de cocinado: receta={}, cantidad={}",
                    event.getRecipeId(), event.getQuantityCooked());

            Recipe recipe = recipeRepository.findById(event.getRecipeId())
                    .orElseThrow(() -> new RuntimeException("Receta no encontrada: " + event.getRecipeId()));

            User user = null;
            if (event.getUserId() != null) {
                user = userRepository.findById(event.getUserId()).orElse(null);
            }

            RecipeCookingAudit audit = new RecipeCookingAudit();
            audit.setRecipe(recipe);
            audit.setUsers(user);
            audit.setQuantityCooked(event.getQuantityCooked());
            audit.setDetails(event.getDetails());
            audit.setComponentsState(event.getComponentsState());
            audit.setCookingDate(event.getCookingDate());

            recipeCookingAuditRepository.save(audit);

            log.info("Auditoría de cocinado guardada: id={}, receta={}, cantidad={}, usuario={}",
                    audit.getId(), event.getRecipeId(), event.getQuantityCooked(), event.getUserName());

        } catch (Exception e) {
            log.error("Error al procesar evento de auditoría de cocinado: {}", e.getMessage(), e);

            throw new RuntimeException("Error procesando auditoría de cocinado", e);
        }
    }
}
