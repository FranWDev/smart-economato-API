package com.economato.inventory.kafka;

import com.economato.inventory.controller.BaseIntegrationTest;
import com.economato.inventory.dto.event.InventoryAuditEvent;
import com.economato.inventory.kafka.producer.AuditEventProducer;
import com.economato.inventory.kafka.producer.AuditOutboxProcessor;
import com.economato.inventory.model.AuditOutbox;
import com.economato.inventory.model.InventoryAudit;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Supplier;
import com.economato.inventory.repository.AuditOutboxRepository;
import com.economato.inventory.repository.InventoryAuditRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.SupplierRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.RecipeRepository;
import com.economato.inventory.repository.RecipeAuditRepository;
import com.economato.inventory.repository.RecipeCookingAuditRepository;
import com.economato.inventory.repository.OrderAuditRepository;
import com.economato.inventory.model.User;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.dto.event.RecipeAuditEvent;
import com.economato.inventory.dto.event.OrderAuditEvent;
import com.economato.inventory.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.model.RecipeAudit;
import com.economato.inventory.model.RecipeCookingAudit;
import com.economato.inventory.model.OrderAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(partitions = 1)
@ActiveProfiles({ "test", "kafka-test" })
// Desactivamos logs molestos de Kafka en tests
@TestPropertySource(properties = {
        "logging.level.org.apache.kafka=ERROR",
        "logging.level.kafka=ERROR",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
// DirtiesContext para que EmbeddedKafka reinicie si es necesario en cada test
// class
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AuditOutboxIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuditEventProducer auditEventProducer;

    @Autowired
    private AuditOutboxProcessor auditOutboxProcessor;

    @Autowired
    private AuditOutboxRepository auditOutboxRepository;

    @Autowired
    private InventoryAuditRepository inventoryAuditRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private RecipeAuditRepository recipeAuditRepository;

    @Autowired
    private OrderAuditRepository orderAuditRepository;

    @Autowired
    private RecipeCookingAuditRepository recipeCookingAuditRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    private Product testProduct;
    private User testUser;
    private Order testOrder;
    private Recipe testRecipe;

    @BeforeEach
    void setupCb() {
        clearDatabase();
        auditOutboxRepository.deleteAll();

        Supplier supplier = new Supplier();
        supplier.setName("Test Supplier");
        supplier = supplierRepository.save(supplier);

        testProduct = new Product();
        testProduct.setName("Test Product");
        testProduct.setSupplier(supplier);
        testProduct.setType("Food");
        testProduct.setUnit("Kg");
        testProduct.setUnitPrice(BigDecimal.TEN);
        testProduct.setProductCode("TEST-01");
        testProduct.setCurrentStock(BigDecimal.TEN);
        testProduct.setMinimumStock(BigDecimal.ONE);
        testProduct = productRepository.save(testProduct);

        testUser = new User();
        testUser.setName("Test User");
        testUser.setUser("testuser");
        testUser.setPassword("password");
        testUser.setRole(com.economato.inventory.model.Role.ADMIN);
        testUser.setFirstLogin(true);
        testUser.setHidden(false);
        testUser = userRepository.save(testUser);

        testOrder = new Order();
        testOrder.setUsers(testUser);
        testOrder.setOrderDate(LocalDateTime.now());
        testOrder.setStatus("PENDING");
        testOrder = orderRepository.save(testOrder);

        testRecipe = new Recipe();
        testRecipe.setName("Test Recipe");
        testRecipe.setTotalCost(BigDecimal.TEN);
        testRecipe = recipeRepository.save(testRecipe);
    }

    @Test
    void testOutboxToKafkaFlow() throws Exception {
        InventoryAuditEvent event = new InventoryAuditEvent();
        event.setProductId(testProduct.getId());
        event.setMovementType("ENTRADA");
        event.setQuantity(BigDecimal.valueOf(5.0));
        event.setMovementDate(LocalDateTime.now());
        event.setActionDescription("Test Kafka Integration");

        auditEventProducer.publishInventoryAudit(event);

        List<AuditOutbox> outboxItems = auditOutboxRepository.findAll();
        assertThat(outboxItems).hasSize(1);
        assertThat(outboxItems.get(0).getTopic()).isEqualTo("inventory-audit-events");

        auditOutboxProcessor.processOutbox();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(auditOutboxRepository.findAll()).isEmpty();
        });

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<InventoryAudit> audits = inventoryAuditRepository.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getProduct().getId()).isEqualTo(testProduct.getId());
            assertThat(audits.get(0).getActionDescription()).isEqualTo("Test Kafka Integration");
        });
    }

    @Test
    void testRecipeOutboxToKafkaFlow() throws Exception {
        RecipeAuditEvent event = new RecipeAuditEvent();
        event.setRecipeId(testRecipe.getId());
        event.setAction("CREATE");
        event.setAuditDate(LocalDateTime.now());
        event.setDetails("Test Recipe Creation via Kafka Integration");

        auditEventProducer.publishRecipeAudit(event);

        List<AuditOutbox> outboxItems = auditOutboxRepository.findAll();
        assertThat(outboxItems).hasSize(1);
        assertThat(outboxItems.get(0).getTopic()).isEqualTo("recipe-audit-events");

        auditOutboxProcessor.processOutbox();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(auditOutboxRepository.findAll()).isEmpty();
        });

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<RecipeAudit> audits = recipeAuditRepository.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getRecipe().getId()).isEqualTo(testRecipe.getId());
            assertThat(audits.get(0).getDetails()).isEqualTo("Test Recipe Creation via Kafka Integration");
        });
    }

    @Test
    void testOrderOutboxToKafkaFlow() throws Exception {
        OrderAuditEvent event = new OrderAuditEvent();
        event.setOrderId(testOrder.getId());
        event.setAction("CREATE");
        event.setAuditDate(LocalDateTime.now());
        event.setDetails("Test Order Creation via Kafka Integration");

        auditEventProducer.publishOrderAudit(event);

        List<AuditOutbox> outboxItems = auditOutboxRepository.findAll();
        assertThat(outboxItems).hasSize(1);
        assertThat(outboxItems.get(0).getTopic()).isEqualTo("order-audit-events");

        auditOutboxProcessor.processOutbox();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(auditOutboxRepository.findAll()).isEmpty();
        });

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OrderAudit> audits = orderAuditRepository.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getOrder().getId()).isEqualTo(testOrder.getId());
            assertThat(audits.get(0).getDetails()).isEqualTo("Test Order Creation via Kafka Integration");
        });
    }

    @Test
    void testCookingOutboxToKafkaFlow() throws Exception {
        RecipeCookingAuditEvent event = new RecipeCookingAuditEvent();
        event.setRecipeId(testRecipe.getId());
        event.setQuantityCooked(BigDecimal.valueOf(2));
        event.setCookingDate(LocalDateTime.now());
        event.setDetails("Test Cooking via Kafka Integration");

        auditEventProducer.publishRecipeCookingAudit(event);

        List<AuditOutbox> outboxItems = auditOutboxRepository.findAll();
        assertThat(outboxItems).hasSize(1);
        assertThat(outboxItems.get(0).getTopic()).isEqualTo("recipe-cooking-audit-events");

        auditOutboxProcessor.processOutbox();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(auditOutboxRepository.findAll()).isEmpty();
        });

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<RecipeCookingAudit> audits = recipeCookingAuditRepository.findAll();
            assertThat(audits).hasSize(1);
            assertThat(audits.get(0).getRecipe().getId()).isEqualTo(testRecipe.getId());
            assertThat(audits.get(0).getDetails()).isEqualTo("Test Cooking via Kafka Integration");
        });
    }
}
