package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.projection.PendingProductQuantity;
import com.economato.inventory.application.dto.response.AlertResolution;
import com.economato.inventory.application.dto.response.AlertSeverity;
import com.economato.inventory.application.dto.response.DailyForecastResponseDTO;
import com.economato.inventory.application.dto.response.StockAlertDTO;
import com.economato.inventory.application.dto.response.WeeklyConsumptionResponseDTO;
import com.economato.inventory.application.mapper.StockDailyForecastMapper;
import com.economato.inventory.application.mapper.StockWeeklyConsumptionHistoryMapper;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.StockDailyForecast;
import com.economato.inventory.domain.model.StockPrediction;
import com.economato.inventory.domain.model.StockWeeklyConsumptionHistory;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockDailyForecastRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockPredictionRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockWeeklyConsumptionHistoryRepository;
import com.economato.inventory.infrastructure.adapter.out.external.prediction.HoltWintersForecaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockAlertServiceTest {

    @Mock
    private RecipeCookingAuditRepository cookingAuditRepository;
    @Mock
    private OrderDetailRepository orderDetailRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    @SuppressWarnings("unused")
    private RecipeRepository recipeRepository;
    @Mock
    private StockPredictionRepository predictionRepository;
    @Mock
    private StockDailyForecastRepository dailyForecastRepository;
    @Mock
    private StockWeeklyConsumptionHistoryRepository weeklyHistoryRepository;
    @Mock
    private StockDailyForecastMapper stockDailyForecastMapper;
    @Mock
    private StockWeeklyConsumptionHistoryMapper stockWeeklyConsumptionHistoryMapper;
    @Mock
    @SuppressWarnings("unused")
    private HoltWintersForecaster forecaster;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StockAlertService stockAlertService;

    @org.junit.jupiter.api.BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        org.mockito.Mockito.lenient().when(messageSource.getMessage(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    if (key.contains("uncovered"))
                        return "Déficit estimado";
                    if (key.contains("partially"))
                        return "Considera ampliar el pedido";
                    return "message";
                });
    }

    @Test
    void getActiveAlerts_whenNoPredictionsInDB_returnsEmptyList() {
        when(predictionRepository.findAll()).thenReturn(List.of());

        List<StockAlertDTO> result = stockAlertService.getActiveAlerts();

        assertTrue(result.isEmpty());
    }

    @Test
    void getActiveAlerts_generatesCriticalAlert_whenStockIsLowAndNoOrders() {
        // --- Setup Data ---
        Integer productId = 101;
        Product product = new Product();
        product.setId(productId);
        product.setName("Tomate");
        product.setUnit("kg");
        product.setCurrentStock(BigDecimal.valueOf(1.0));
        product.setHidden(false);

        // Saved prediction: 16kg for 14 days
        StockPrediction prediction = StockPrediction
                .builder()
                .id(productId)
                .product(product)
                .projectedConsumption(BigDecimal.valueOf(16.0))
                .build();

        // --- Mocks ---
        when(predictionRepository.findAll()).thenReturn(List.of(prediction));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(cookingAuditRepository.findTopConsumingRecipesByProduct(eq(productId), any()))
                .thenReturn(List.of("Gazpacho"));

        // --- Execute ---
        List<StockAlertDTO> alerts = stockAlertService.getActiveAlerts();

        // --- Verify ---
        assertFalse(alerts.isEmpty());
        StockAlertDTO alert = alerts.get(0);
        assertEquals("Tomate", alert.getProductName());
        assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
        assertEquals(AlertResolution.UNCOVERED, alert.getResolution());
        assertTrue(alert.getMessage().contains("Déficit estimado"));
        assertEquals(BigDecimal.valueOf(15.0).setScale(3), alert.getEffectiveGap());
    }

    @Test
    void getActiveAlerts_generatesCoveredAlert_whenPendingOrderIsEnough() {
        // --- Setup Data ---
        Integer productId = 202;
        Product product = new Product();
        product.setId(productId);
        product.setName("Arroz");
        product.setUnit("kg");
        product.setCurrentStock(BigDecimal.valueOf(2.0));
        product.setHidden(false);

        // Proyected 10kg for 14 days.
        StockPrediction prediction = StockPrediction
                .builder()
                .id(productId)
                .product(product)
                .projectedConsumption(BigDecimal.valueOf(10.0))
                .build();

        // Current 2.0 + Pending 15.0 = 17.0 (Enough!)
        PendingProductQuantity pending = mock(PendingProductQuantity.class);
        when(pending.getProductId()).thenReturn(productId);
        when(pending.getPendingQuantity()).thenReturn(BigDecimal.valueOf(15.0));

        // --- Mocks ---
        when(predictionRepository.findAll()).thenReturn(List.of(prediction));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of(pending));
        when(productRepository.findAll()).thenReturn(List.of(product));

        // --- Execute ---
        // DaysRemaining = (2+15) / (10/14) = 17 / 0.714 = ~23.8 days -> OK
        List<StockAlertDTO> alerts = stockAlertService.getActiveAlerts();

        // --- Verify ---
        assertTrue(alerts.isEmpty(), "If days covered >= 21, severity is OK and alert is filtered out");
    }

    @Test
    void getActiveAlerts_generatesPartiallyCoveredAlert_whenPendingOrderIsNotEnough() {
        // --- Setup Data ---
        Integer productId = 303;
        Product product = new Product();
        product.setId(productId);
        product.setName("Aceite");
        product.setUnit("L");
        product.setCurrentStock(BigDecimal.valueOf(1.0));
        product.setHidden(false);

        // Proyected 20L for 14 days.
        StockPrediction prediction = StockPrediction
                .builder()
                .id(productId)
                .product(product)
                .projectedConsumption(BigDecimal.valueOf(20.0))
                .build();

        // Current 1.0 + Pending 4.0 = 5.0L (Deficit of 15L!)
        PendingProductQuantity pending = mock(PendingProductQuantity.class);
        when(pending.getProductId()).thenReturn(productId);
        when(pending.getPendingQuantity()).thenReturn(BigDecimal.valueOf(4.0));

        // --- Mocks ---
        when(predictionRepository.findAll()).thenReturn(List.of(prediction));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of(pending));
        when(productRepository.findAll()).thenReturn(List.of(product));

        // --- Execute ---
        List<StockAlertDTO> alerts = stockAlertService.getActiveAlerts();

        // --- Verify ---
        assertFalse(alerts.isEmpty());
        assertEquals(AlertResolution.PARTIALLY_COVERED, alerts.get(0).getResolution());
        assertEquals(AlertSeverity.HIGH, alerts.get(0).getSeverity());
    }

    @Test
    void verifyAllSeverityLevels() {
        // Test thresholds:
        // < 3 -> CRITICAL
        // 3-6 -> HIGH
        // 7-13 -> MEDIUM
        // 14-20 -> LOW
        // >= 21 -> OK (Filtered out)

        checkSeverity(1.0, 20.0, AlertSeverity.CRITICAL); // Days: 1 / (20/14) = 0.7
        checkSeverity(5.0, 20.0, AlertSeverity.HIGH); // Days: 5 / (20/14) = 3.5
        checkSeverity(10.0, 20.0, AlertSeverity.MEDIUM); // Days: 10 / (20/14) = 7.0
        checkSeverity(25.0, 20.0, AlertSeverity.LOW); // Days: 25 / (20/14) = 17.5
        checkSeverity(35.0, 20.0, null); // Days: 35 / (20/14) = 24.5 -> OK -> Filtered
    }

    private void checkSeverity(double effectiveStock, double projected14Days, AlertSeverity expected) {
        Integer productId = 999;
        Product product = new Product();
        product.setId(productId);
        product.setName("Test Product");
        product.setCurrentStock(BigDecimal.valueOf(effectiveStock));
        product.setHidden(false);

        StockPrediction prediction = StockPrediction
                .builder()
                .id(productId)
                .product(product)
                .projectedConsumption(BigDecimal.valueOf(projected14Days))
                .build();

        when(predictionRepository.findAll()).thenReturn(List.of(prediction));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<StockAlertDTO> alerts = stockAlertService.getActiveAlerts();

        if (expected == null) {
            assertTrue(alerts.isEmpty());
        } else {
            assertFalse(alerts.isEmpty());
            assertEquals(expected, alerts.get(0).getSeverity());
        }
    }

    @Test
    void getWeeklyConsumptionHistoryAll_returnsMappedWeeklySeriesForAllProducts() {
        Integer p1 = 1;
        Integer p2 = 2;

        StockWeeklyConsumptionHistory h1 = StockWeeklyConsumptionHistory.builder().id(p1).build();
        StockWeeklyConsumptionHistory h2 = StockWeeklyConsumptionHistory.builder().id(p2).build();
        WeeklyConsumptionResponseDTO dto1 = WeeklyConsumptionResponseDTO.builder().productId(p1).productName("Tomate").unit("kg")
            .weeklyConsumption(List.of(BigDecimal.valueOf(2.5).setScale(3), BigDecimal.ZERO.setScale(3), BigDecimal.ONE.setScale(3)))
            .weeksOfHistory(12)
            .build();
        WeeklyConsumptionResponseDTO dto2 = WeeklyConsumptionResponseDTO.builder().productId(p2).productName("Aceite").unit("L")
            .weeklyConsumption(List.of(BigDecimal.valueOf(4.0).setScale(3)))
            .weeksOfHistory(12)
            .build();

        when(weeklyHistoryRepository.findAll()).thenReturn(List.of(h1, h2));
        when(stockWeeklyConsumptionHistoryMapper.toDTO(h1)).thenReturn(dto1);
        when(stockWeeklyConsumptionHistoryMapper.toDTO(h2)).thenReturn(dto2);

        List<WeeklyConsumptionResponseDTO> result = stockAlertService.getWeeklyConsumptionHistoryAll();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getProductId().equals(p1)));
        assertTrue(result.stream().anyMatch(r -> r.getProductId().equals(p2)));

        WeeklyConsumptionResponseDTO tomato = result.stream()
                .filter(r -> r.getProductId().equals(p1))
                .findFirst()
                .orElseThrow();
        assertEquals("Tomate", tomato.getProductName());
        assertEquals("kg", tomato.getUnit());
        assertEquals(12, tomato.getWeeksOfHistory());
        assertEquals(List.of(BigDecimal.valueOf(2.5).setScale(3), BigDecimal.ZERO.setScale(3), BigDecimal.ONE.setScale(3)),
            tomato.getWeeklyConsumption());
    }

    @Test
    void getWeeklyConsumptionHistory_withProductId_filtersByProduct() {
        Integer p1 = 10;
        StockWeeklyConsumptionHistory h1 = StockWeeklyConsumptionHistory.builder().id(p1).build();
        WeeklyConsumptionResponseDTO dto = WeeklyConsumptionResponseDTO.builder().productId(p1).productName("Harina").unit("kg")
            .weeklyConsumption(List.of(BigDecimal.valueOf(3.0).setScale(3)))
            .weeksOfHistory(12)
            .build();

        when(weeklyHistoryRepository.findOneById(p1)).thenReturn(Optional.of(h1));
        when(stockWeeklyConsumptionHistoryMapper.toDTO(h1)).thenReturn(dto);

        List<WeeklyConsumptionResponseDTO> result = stockAlertService.getWeeklyConsumptionHistory(p1);

        assertEquals(1, result.size());
        assertEquals(p1, result.get(0).getProductId());
    }

    @Test
    void getDailyForecast_whenProductHasHistory_returnsForecastDto() {
        Integer productId = 50;
        StockDailyForecast entity = StockDailyForecast.builder().id(productId).build();
        DailyForecastResponseDTO dto = DailyForecastResponseDTO.builder()
            .productId(productId)
            .productName("Leche")
            .unit("L")
            .dailyForecast(List.of(BigDecimal.valueOf(1.0), BigDecimal.valueOf(1.1)))
            .horizonDays(14)
            .build();

        when(dailyForecastRepository.findOneById(productId)).thenReturn(Optional.of(entity));
        when(stockDailyForecastMapper.toDTO(entity)).thenReturn(dto);

        Optional<DailyForecastResponseDTO> result = stockAlertService.getDailyForecast(productId);

        assertTrue(result.isPresent());
        assertEquals(productId, result.get().getProductId());
        assertEquals("Leche", result.get().getProductName());
        assertEquals("L", result.get().getUnit());
        assertEquals(14, result.get().getHorizonDays());
        assertEquals(2, result.get().getDailyForecast().size());
    }

    @Test
    void getDailyForecast_whenNoHistory_returnsEmpty() {
        when(dailyForecastRepository.findOneById(999)).thenReturn(Optional.empty());

        Optional<DailyForecastResponseDTO> result = stockAlertService.getDailyForecast(999);

        assertTrue(result.isEmpty());
    }

    @Test
    void getDailyForecastAll_returnsDtosForAllProductsWithHistory() {
        Integer p1 = 70;
        Integer p2 = 80;

        StockDailyForecast f1 = StockDailyForecast.builder().id(p1).build();
        StockDailyForecast f2 = StockDailyForecast.builder().id(p2).build();
        DailyForecastResponseDTO dto1 = DailyForecastResponseDTO.builder().productId(p1)
            .dailyForecast(List.of(BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.5)))
            .horizonDays(14)
            .build();
        DailyForecastResponseDTO dto2 = DailyForecastResponseDTO.builder().productId(p2)
            .dailyForecast(List.of(BigDecimal.valueOf(0.7), BigDecimal.valueOf(0.7)))
            .horizonDays(14)
            .build();

        when(dailyForecastRepository.findAll()).thenReturn(List.of(f1, f2));
        when(stockDailyForecastMapper.toDTO(f1)).thenReturn(dto1);
        when(stockDailyForecastMapper.toDTO(f2)).thenReturn(dto2);

        List<DailyForecastResponseDTO> result = stockAlertService.getDailyForecastAll();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getProductId().equals(p1)));
        assertTrue(result.stream().anyMatch(r -> r.getProductId().equals(p2)));
        assertTrue(result.stream().allMatch(r -> r.getDailyForecast().size() == 2));
    }
}
