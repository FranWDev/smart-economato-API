package com.economato.inventory.application.usecase.mcp;

import com.economato.inventory.application.dto.mcp.McpCostBreakdownDto;
import com.economato.inventory.application.dto.mcp.McpMenuSuggestionDto;
import com.economato.inventory.application.dto.mcp.McpReorderSuggestionDto;
import com.economato.inventory.application.dto.mcp.McpStockHealthDto;
import com.economato.inventory.application.dto.projection.PendingProductQuantity;
import com.economato.inventory.application.dto.response.AlertResolution;
import com.economato.inventory.application.dto.response.AlertSeverity;
import com.economato.inventory.application.dto.response.StockAlertDTO;
import com.economato.inventory.application.usecase.ProductBatchService;
import com.economato.inventory.application.usecase.StockAlertService;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.StockPrediction;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockPredictionRepository;
import com.economato.inventory.infrastructure.config.ai.AiAnalysisProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpAnalysisServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private StockPredictionRepository stockPredictionRepository;
    @Mock
    private OrderDetailRepository orderDetailRepository;
    @Mock
    private StockLedgerRepository stockLedgerRepository;
    @Mock
    private RecipeCookingAuditRepository recipeCookingAuditRepository;
    @Mock
    private ProductBatchService productBatchService;
    @Mock
    private StockAlertService stockAlertService;
    @Mock
    private AiAnalysisProperties aiAnalysisProperties;

    @InjectMocks
    private McpAnalysisService service;

    @Test
    void getReorderSuggestions_ShouldCalculateSuggestedQuantityAndUrgency() {
        Product product = new Product();
        product.setId(1);
        product.setName("Rice");
        product.setCurrentStock(new BigDecimal("10.000"));
        when(productRepository.findAllActive()).thenReturn(List.of(product));

        StockPrediction prediction = new StockPrediction();
        prediction.setId(1);
        prediction.setProjectedConsumption(new BigDecimal("30.000"));
        when(stockPredictionRepository.findAll()).thenReturn(List.of(prediction));

        PendingProductQuantity pending = new PendingProductQuantity() {
            @Override
            public Integer getProductId() {
                return 1;
            }

            @Override
            public BigDecimal getPendingQuantity() {
                return new BigDecimal("5.000");
            }
        };
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of(pending));
        when(aiAnalysisProperties.getReorderSuggestionHorizonDays()).thenReturn(14);

        List<McpReorderSuggestionDto> result = service.getReorderSuggestions();

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).productId());
        assertEquals(new BigDecimal("18.000"), result.get(0).suggestedQuantity());
        assertEquals("HIGH", result.get(0).urgency());
    }

    @Test
    void getCostBreakdown_WhenNoActiveProducts_ShouldReturnZeroSummary() {
        when(productRepository.findAllActive()).thenReturn(List.of());

        McpCostBreakdownDto result = service.getCostBreakdown(LocalDate.now().minusDays(30), LocalDate.now());

        assertEquals(BigDecimal.ZERO, result.totalCost());
        assertEquals(BigDecimal.ZERO, result.dailyAverage());
        assertTrue(result.costByProduct().isEmpty());
    }

    @Test
    void getStockHealthScore_ShouldAggregateRatiosIntoScore() {
        Product p1 = new Product();
        p1.setId(1);
        p1.setCurrentStock(new BigDecimal("20.000"));
        Product p2 = new Product();
        p2.setId(2);
        p2.setCurrentStock(new BigDecimal("5.000"));
        when(productRepository.findAllActive()).thenReturn(List.of(p1, p2));

        StockPrediction pred1 = new StockPrediction();
        pred1.setId(1);
        pred1.setProjectedConsumption(new BigDecimal("10.000"));
        StockPrediction pred2 = new StockPrediction();
        pred2.setId(2);
        pred2.setProjectedConsumption(new BigDecimal("8.000"));
        when(stockPredictionRepository.findAll()).thenReturn(List.of(pred1, pred2));

        when(aiAnalysisProperties.getWasteRiskDaysThreshold()).thenReturn(7);

        ProductBatch b1 = ProductBatch.builder().id(1L).product(p1).expirationDate(LocalDate.now().plusDays(1)).remainingQuantity(BigDecimal.ONE).build();
        ProductBatch b2 = ProductBatch.builder().id(2L).product(p1).expirationDate(LocalDate.now().plusDays(10)).remainingQuantity(BigDecimal.ONE).build();
        ProductBatch b3 = ProductBatch.builder().id(3L).product(p2).expirationDate(LocalDate.now().plusDays(12)).remainingQuantity(BigDecimal.ONE).build();
        when(productBatchService.getExpiringBatches(7)).thenReturn(List.of(b1));
        when(productBatchService.getAllActiveBatches()).thenReturn(List.of(b1, b2, b3));
        when(aiAnalysisProperties.getStockHealthStockWeight()).thenReturn(0.5);
        when(aiAnalysisProperties.getStockHealthBatchWeight()).thenReturn(0.3);
        when(aiAnalysisProperties.getStockHealthAlertWeight()).thenReturn(0.2);

        StockAlertDTO alert = StockAlertDTO.builder()
                .productId(2)
                .productName("P2")
                .severity(AlertSeverity.HIGH)
            .resolution(AlertResolution.UNCOVERED)
                .build();
        when(stockAlertService.getActiveAlerts()).thenReturn(List.of(alert));

        McpStockHealthDto result = service.getStockHealthScore();

        assertEquals(55, result.score());
        assertEquals(1, result.productsAbovePrediction());
        assertEquals(2, result.totalProducts());
        assertEquals(2, result.batchesWithoutExpiryRisk());
        assertEquals(3, result.totalActiveBatches());
        assertEquals(1, result.alertsResolved());
        assertEquals(1, result.totalAlerts());
    }

    @Test
    void getStockHealthScore_ShouldUseConfiguredWeights() {
        Product p1 = new Product();
        p1.setId(1);
        p1.setCurrentStock(new BigDecimal("20.000"));
        Product p2 = new Product();
        p2.setId(2);
        p2.setCurrentStock(new BigDecimal("5.000"));
        when(productRepository.findAllActive()).thenReturn(List.of(p1, p2));

        StockPrediction pred1 = new StockPrediction();
        pred1.setId(1);
        pred1.setProjectedConsumption(new BigDecimal("10.000"));
        StockPrediction pred2 = new StockPrediction();
        pred2.setId(2);
        pred2.setProjectedConsumption(new BigDecimal("8.000"));
        when(stockPredictionRepository.findAll()).thenReturn(List.of(pred1, pred2));

        when(aiAnalysisProperties.getWasteRiskDaysThreshold()).thenReturn(7);
        when(aiAnalysisProperties.getStockHealthStockWeight()).thenReturn(1.0);
        when(aiAnalysisProperties.getStockHealthBatchWeight()).thenReturn(0.0);
        when(aiAnalysisProperties.getStockHealthAlertWeight()).thenReturn(0.0);

        ProductBatch b1 = ProductBatch.builder().id(1L).product(p1).expirationDate(LocalDate.now().plusDays(1)).remainingQuantity(BigDecimal.ONE).build();
        ProductBatch b2 = ProductBatch.builder().id(2L).product(p1).expirationDate(LocalDate.now().plusDays(10)).remainingQuantity(BigDecimal.ONE).build();
        ProductBatch b3 = ProductBatch.builder().id(3L).product(p2).expirationDate(LocalDate.now().plusDays(12)).remainingQuantity(BigDecimal.ONE).build();
        when(productBatchService.getExpiringBatches(7)).thenReturn(List.of(b1));
        when(productBatchService.getAllActiveBatches()).thenReturn(List.of(b1, b2, b3));

        StockAlertDTO alert = StockAlertDTO.builder()
                .productId(2)
                .productName("P2")
                .severity(AlertSeverity.HIGH)
                .resolution(AlertResolution.UNCOVERED)
                .build();
        when(stockAlertService.getActiveAlerts()).thenReturn(List.of(alert));

        McpStockHealthDto result = service.getStockHealthScore();

        assertEquals(50, result.score());
        assertEquals(1, result.productsAbovePrediction());
        assertEquals(2, result.totalProducts());
        assertEquals(3, result.totalActiveBatches());
    }

    @Test
    void getMenuOptimizer_ShouldSortBeforeLimitingCheapestRecipes() {
        Recipe expensive = new Recipe();
        expensive.setId(1);
        expensive.setName("Expensive");
        expensive.setTotalCost(new BigDecimal("100.00"));
        expensive.setPortions(new BigDecimal("1.00"));
        expensive.setAllergens(Set.of());

        Recipe cheap = new Recipe();
        cheap.setId(2);
        cheap.setName("Cheap");
        cheap.setTotalCost(new BigDecimal("1.00"));
        cheap.setPortions(new BigDecimal("1.00"));
        cheap.setAllergens(Set.of());

        Recipe mid = new Recipe();
        mid.setId(3);
        mid.setName("Mid");
        mid.setTotalCost(new BigDecimal("2.00"));
        mid.setPortions(new BigDecimal("1.00"));
        mid.setAllergens(new HashSet<>());

        when(recipeRepository.findAll()).thenReturn(List.of(expensive, cheap, mid));
        when(aiAnalysisProperties.getMenuOptimizerMaxRecipes()).thenReturn(2);

        McpMenuSuggestionDto result = service.getMenuOptimizer(new BigDecimal("100"), List.of());

        assertEquals(5, result.days().size());
        assertEquals("Cheap", result.days().get(0).recipes().get(0).recipeName());
        assertEquals("Mid", result.days().get(1).recipes().get(0).recipeName());
        assertEquals("Cheap", result.days().get(2).recipes().get(0).recipeName());
    }
}