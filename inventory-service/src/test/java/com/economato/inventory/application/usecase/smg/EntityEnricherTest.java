package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.dto.response.AlertSeverity;
import com.economato.inventory.application.dto.response.StockAlertDTO;
import com.economato.inventory.application.usecase.ProductBatchService;
import com.economato.inventory.application.usecase.StockAlertService;
import com.economato.inventory.application.usecase.smg.model.EntityMemory;
import com.economato.inventory.application.usecase.smg.model.OrderSnapshot;
import com.economato.inventory.application.usecase.smg.model.ProductSnapshot;
import com.economato.inventory.application.usecase.smg.model.RecipeSnapshot;
import com.economato.inventory.domain.model.Allergen;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.OrderDetail;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeComponent;
import com.economato.inventory.domain.model.StockPrediction;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockPredictionRepository;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityEnricherTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockPredictionRepository stockPredictionRepository;

    @Mock
    private StockAlertService stockAlertService;

    @Mock
    private ProductBatchService productBatchService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EntityEnricher entityEnricher;

    @BeforeEach
    void setUp() {
        AiSmgProperties properties = new AiSmgProperties();
        properties.setEntityCacheTtlSeconds(30);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        entityEnricher = new EntityEnricher(
                productRepository,
                recipeRepository,
                orderRepository,
                stockPredictionRepository,
                stockAlertService,
                productBatchService,
                stringRedisTemplate,
                properties,
                objectMapper
        );
    }

    @Test
    void enrich_usesCachedProductAndBuildsRecipeAndOrderSnapshotsFromDb() throws Exception {
        ProductSnapshot cachedSnapshot = new ProductSnapshot(
                10,
                "Tomate Cacheado",
                new BigDecimal("8.500"),
                "kg",
                new BigDecimal("2.10"),
                "LOW",
                new BigDecimal("4.000"),
                3
        );

        Recipe recipe = new Recipe();
        recipe.setId(20);
        recipe.setName("Ensalada");
        recipe.setTotalCost(new BigDecimal("5.50"));
        RecipeComponent component = new RecipeComponent();
        Product recipeProduct = new Product();
        recipeProduct.setId(10);
        recipeProduct.setName("Tomate");
        component.setProduct(recipeProduct);
        component.setQuantity(new BigDecimal("1.500"));
        recipe.getComponents().add(component);
        Allergen allergen = new Allergen();
        allergen.setName("gluten");
        recipe.getAllergens().add(allergen);

        Order order = new Order();
        order.setId(30);
        order.setStatus(OrderStatus.CONFIRMED);
        Supplier supplier = Supplier.builder().name("Proveedor Central").build();
        order.setSupplier(supplier);
        OrderDetail detail = new OrderDetail();
        Product orderProduct = new Product();
        orderProduct.setId(10);
        orderProduct.setUnitPrice(new BigDecimal("2.10"));
        detail.setProduct(orderProduct);
        detail.setQuantity(new BigDecimal("3.000"));
        order.getDetails().add(detail);

        EntityMemory memory = new EntityMemory();
        memory.addProductById(10);
        memory.addRecipeById(20);
        memory.addOrderById(30);

        when(valueOperations.get("ai:entity:product:10")).thenReturn(objectMapper.writeValueAsString(cachedSnapshot));
        when(recipeRepository.findByIdWithDetails(20)).thenReturn(Optional.of(recipe));
        when(orderRepository.findByIdWithDetails(30)).thenReturn(Optional.of(order));

        entityEnricher.enrich(memory);

        assertEquals("Tomate Cacheado", memory.getProducts().get(10).name());
        assertEquals("Ensalada", memory.getRecipes().get(20).name());
        assertEquals("Proveedor Central", memory.getOrders().get(30).supplierName());
        assertTrue(memory.getRecipes().get(20).allergens().contains("gluten"));
        assertTrue(memory.getRecipes().get(20).componentNeeds().containsKey(10));

        verify(productRepository, never()).findByIdWithSupplier(any());
        verify(valueOperations, never()).set(eq("ai:entity:product:10"), anyString(), eq(30L), eq(TimeUnit.SECONDS));
        verify(recipeRepository).findByIdWithDetails(20);
        verify(orderRepository).findByIdWithDetails(30);
    }

    @Test
    void enrich_cachesProductSnapshotOnDatabaseMiss() throws Exception {
        Product product = new Product();
        product.setId(11);
        product.setName("Queso Curado");
        product.setCurrentStock(new BigDecimal("15.000"));
        product.setUnit("kg");
        product.setUnitPrice(new BigDecimal("6.75"));

        StockPrediction prediction = StockPrediction.builder()
                .id(11)
                .product(product)
                .projectedConsumption(new BigDecimal("4.200"))
                .updatedAt(LocalDateTime.now())
                .build();

        ProductBatch batch = ProductBatch.builder()
                .id(501L)
                .product(product)
                .expirationDate(LocalDate.now().plusDays(2))
                .initialQuantity(new BigDecimal("10.000"))
                .remainingQuantity(new BigDecimal("7.500"))
                .receivedAt(LocalDateTime.now().minusDays(1))
                .depleted(false)
                .build();

        StockAlertDTO alert = StockAlertDTO.builder()
                .productId(11)
                .productName("Queso Curado")
                .severity(AlertSeverity.CRITICAL)
                .build();

        EntityMemory memory = new EntityMemory();
        memory.addProductById(11);

        when(valueOperations.get("ai:entity:product:11")).thenReturn(null);
        when(productRepository.findByIdWithSupplier(11)).thenReturn(Optional.of(product));
        when(stockPredictionRepository.findById(11)).thenReturn(Optional.of(prediction));
        when(stockAlertService.getAlertByProductId(11)).thenReturn(Optional.of(alert));
        when(productBatchService.getActiveBatches(11)).thenReturn(List.of(batch));

        entityEnricher.enrich(memory);

        assertEquals("Queso Curado", memory.getProducts().get(11).name());
        verify(valueOperations).set(eq("ai:entity:product:11"), anyString(), eq(30L), eq(TimeUnit.SECONDS));
        verify(productRepository).findByIdWithSupplier(11);
    }
}