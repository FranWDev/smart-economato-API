package com.economato.inventory.application.usecase.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.economato.inventory.application.dto.mcp.McpExpiringBatchDto;
import com.economato.inventory.application.dto.mcp.McpLedgerEntryDto;
import com.economato.inventory.application.dto.mcp.McpSupplierDeepDto;
import com.economato.inventory.application.usecase.ProductBatchService;
import com.economato.inventory.application.usecase.StockAlertService;
import com.economato.inventory.application.usecase.WeeklyPlanService;
import com.economato.inventory.domain.model.FoodCrisis;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockDailyForecast;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.StockWeeklyConsumptionHistory;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockDailyForecastRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockPredictionRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockWeeklyConsumptionHistoryRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.config.ai.AiAnalysisProperties;

@ExtendWith(MockitoExtension.class)
class McpToolReadServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StockPredictionRepository stockPredictionRepository;
    @Mock
    private StockDailyForecastRepository stockDailyForecastRepository;
    @Mock
    private StockWeeklyConsumptionHistoryRepository stockWeeklyConsumptionHistoryRepository;
    @Mock
    private StockLedgerRepository stockLedgerRepository;
    @Mock
    private RecipeCookingAuditRepository recipeCookingAuditRepository;
    @Mock
    private FoodCrisisRepository foodCrisisRepository;
    @Mock
    private ProductBatchService productBatchService;
    @Mock
    private StockAlertService stockAlertService;
    @Mock
    private WeeklyPlanService weeklyPlanService;
    @Mock
    private AiAnalysisProperties aiAnalysisProperties;

    @InjectMocks
    private McpToolReadService service;

    @Test
    void getDefaultReorderHorizonDays_ShouldReadConfigValue() {
        when(aiAnalysisProperties.getReorderSuggestionHorizonDays()).thenReturn(21);

        int result = service.getDefaultReorderHorizonDays();

        assertEquals(21, result);
    }

    @Test
    void getProductForecast_ShouldReturnPersistedSeries() {
        StockDailyForecast forecast = new StockDailyForecast();
        forecast.setDailyForecast(List.of(new BigDecimal("1.5"), new BigDecimal("2.0")));
        when(stockDailyForecastRepository.findOneById(4)).thenReturn(Optional.of(forecast));

        List<BigDecimal> result = service.getProductForecast(4);

        assertEquals(2, result.size());
        assertEquals(new BigDecimal("1.5"), result.get(0));
    }

    @Test
    void getProductConsumptionHistory_WhenNoHistory_ShouldReturnEmptyList() {
        when(stockWeeklyConsumptionHistoryRepository.findOneById(8)).thenReturn(Optional.empty());

        List<BigDecimal> result = service.getProductConsumptionHistory(8);

        assertTrue(result.isEmpty());
    }

    @Test
    void getProductConsumptionHistory_ShouldReturnPersistedHistory() {
        StockWeeklyConsumptionHistory history = new StockWeeklyConsumptionHistory();
        history.setWeeklyConsumption(List.of(new BigDecimal("12.000")));
        when(stockWeeklyConsumptionHistoryRepository.findOneById(8)).thenReturn(Optional.of(history));

        List<BigDecimal> result = service.getProductConsumptionHistory(8);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("12.000"), result.get(0));
    }

    @Test
    void getExpiringSoon_WhenDaysBelowOne_ShouldUseMinimumThreshold() {
        Product product = new Product();
        product.setId(3);
        product.setName("Milk");

        ProductBatch batch = ProductBatch.builder()
                .id(9L)
                .product(product)
                .expirationDate(LocalDate.now().plusDays(2))
                .remainingQuantity(new BigDecimal("5.000"))
                .depleted(false)
                .build();
        when(productBatchService.getExpiringBatches(1)).thenReturn(List.of(batch));

        List<McpExpiringBatchDto> result = service.getExpiringSoon(0);

        verify(productBatchService).getExpiringBatches(1);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).productId());
    }

    @Test
    void getProductLedger_ShouldClampLimitAndMapEntries() {
        User user = new User();
        user.setName("Admin");

        StockLedger ledger = StockLedger.builder()
                .id(70L)
                .movementType(MovementType.SALIDA)
                .quantityDelta(new BigDecimal("-2.000"))
                .resultingStock(new BigDecimal("18.000"))
                .description("consumption")
                .transactionTimestamp(LocalDateTime.of(2025, 2, 1, 10, 0))
                .user(user)
                .build();
        when(stockLedgerRepository.findByProductId(any(), any())).thenReturn(new PageImpl<>(List.of(ledger)));

        List<McpLedgerEntryDto> result = service.getProductLedger(1, 1000);

        assertEquals(1, result.size());
        assertEquals("SALIDA", result.get(0).movementType());
        assertEquals("Admin", result.get(0).userName());
    }

    @Test
    void getSupplierDeep_ShouldUseSupplierScopedQueries() {
        Supplier supplier = new Supplier();
        supplier.setId(3);
        supplier.setName("Proveedor Central");
        supplier.setPhone("555-123");
        supplier.setEmail("proveedor@test.local");
        when(supplierRepository.findById(3)).thenReturn(Optional.of(supplier));

        Product product = new Product();
        product.setId(42);
        product.setName("Tomate");
        product.setProductCode("P42");
        product.setCurrentStock(new BigDecimal("10.000"));
        product.setUnit("kg");
        product.setUnitPrice(new BigDecimal("2.50"));
        product.setLotQuantity(new BigDecimal("1.000"));
        when(productRepository.findBySupplierId(3)).thenReturn(List.of(product));
        when(orderRepository.countBySupplierId(3)).thenReturn(2L);
        when(foodCrisisRepository.existsByStatusAndSupplierId(FoodCrisis.CrisisStatus.ACTIVE, 3)).thenReturn(false);

        McpSupplierDeepDto result = service.getSupplierDeep(3);

        assertEquals(3, result.getId());
        assertEquals(1, result.getProducts().size());
        assertEquals("Tomate", result.getProducts().get(0).getName());
        assertEquals(2, result.getRecentOrderCount());
        assertFalse(result.isHasCrisis());
        verify(productRepository).findBySupplierId(3);
        verify(orderRepository).countBySupplierId(3);
        verify(productRepository, never()).findAll();
        verify(orderRepository, never()).findAll();
    }
}