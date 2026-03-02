package com.economato.inventory.service;

import com.economato.inventory.dto.projection.PendingProductQuantity;
import com.economato.inventory.dto.projection.WeeklyIngredientConsumption;
import com.economato.inventory.dto.response.AlertResolution;
import com.economato.inventory.dto.response.AlertSeverity;
import com.economato.inventory.dto.response.StockAlertDTO;
import com.economato.inventory.model.Product;
import com.economato.inventory.repository.OrderDetailRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeCookingAuditRepository;
import com.economato.inventory.service.prediction.HoltWintersForecaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    private HoltWintersForecaster forecaster;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StockAlertService stockAlertService;

    @org.junit.jupiter.api.BeforeEach
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
    void getActiveAlerts_whenNoConsumptionData_returnsEmptyList() {
        when(cookingAuditRepository.findWeeklyConsumptionPerIngredient(any(), any())).thenReturn(List.of());

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

        // Weekly consumption: 8kg/week consistently
        WeeklyIngredientConsumption row = mock(WeeklyIngredientConsumption.class);
        when(row.getProductId()).thenReturn(productId);
        when(row.getWeekIndex()).thenReturn(0);
        when(row.getTotalConsumed()).thenReturn(BigDecimal.valueOf(8.0));

        // Proyected: 16kg for 14 days
        double projectedConsumptionFor14Days = 16.0;

        // --- Mocks ---
        when(cookingAuditRepository.findWeeklyConsumptionPerIngredient(any(), any())).thenReturn(List.of(row));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of());
        when(forecaster.forecast(anyList(), anyInt(), anyInt())).thenReturn(projectedConsumptionFor14Days);
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

        WeeklyIngredientConsumption row = mock(WeeklyIngredientConsumption.class);
        when(row.getProductId()).thenReturn(productId);
        when(row.getWeekIndex()).thenReturn(0);
        when(row.getTotalConsumed()).thenReturn(BigDecimal.valueOf(5.0));

        // Proyected 10kg for 14 days.
        // Current 2.0 + Pending 15.0 = 17.0 (Enough!)
        double projected = 10.0;

        PendingProductQuantity pending = mock(PendingProductQuantity.class);
        when(pending.getProductId()).thenReturn(productId);
        when(pending.getPendingQuantity()).thenReturn(BigDecimal.valueOf(15.0));

        // --- Mocks ---
        when(cookingAuditRepository.findWeeklyConsumptionPerIngredient(any(), any())).thenReturn(List.of(row));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of(pending));
        when(forecaster.forecast(anyList(), anyInt(), anyInt())).thenReturn(projected);

        // --- Execute ---
        // Note: The logic in StockAlertService says if severity == OK, it's filtered
        // out from getActiveAlerts.
        // classifySeverity(daysRemaining).
        // DaysRemaining = (2+15) / (10/14) = 17 / 0.714 = ~23.8 days.
        // classifySeverity(23) -> AlertSeverity.OK.

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

        WeeklyIngredientConsumption row = mock(WeeklyIngredientConsumption.class);
        when(row.getProductId()).thenReturn(productId);
        when(row.getWeekIndex()).thenReturn(0);
        when(row.getTotalConsumed()).thenReturn(BigDecimal.valueOf(10.0));

        // Proyected 20L for 14 days.
        // Current 1.0 + Pending 4.0 = 5.0L (Deficit of 15L!)
        // DaysRemaining = 5 / (20/14) = 5 / 1.42 = 3.5 days -> HIGH severity
        double projected = 20.0;

        PendingProductQuantity pending = mock(PendingProductQuantity.class);
        when(pending.getProductId()).thenReturn(productId);
        when(pending.getPendingQuantity()).thenReturn(BigDecimal.valueOf(4.0));

        // --- Mocks ---
        when(cookingAuditRepository.findWeeklyConsumptionPerIngredient(any(), any())).thenReturn(List.of(row));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of(pending));
        when(forecaster.forecast(anyList(), anyInt(), anyInt())).thenReturn(projected);

        // --- Execute ---
        List<StockAlertDTO> alerts = stockAlertService.getActiveAlerts();

        // --- Verify ---
        assertFalse(alerts.isEmpty());
        assertEquals(AlertResolution.PARTIALLY_COVERED, alerts.get(0).getResolution());
        assertEquals(AlertSeverity.HIGH, alerts.get(0).getSeverity());
        assertTrue(alerts.get(0).getMessage().contains("Considera ampliar el pedido"));
    }

    @Test
    void verifyAllSeverityLevels() {
        // Test thresholds:
        // < 3 -> CRITICAL
        // 3-6 -> HIGH
        // 7-13 -> MEDIUM
        // 14-20 -> LOW
        // >= 21 -> OK (Filtered out)

        // Helper to run a test case
        checkSeverity(1.0, 20.0, AlertSeverity.CRITICAL); // Days: 1 / (20/14) = 0.7
        checkSeverity(5.0, 20.0, AlertSeverity.HIGH); // Days: 5 / (20/14) = 3.5
        checkSeverity(10.0, 20.0, AlertSeverity.MEDIUM); // Days: 10 / (20/14) = 7.0
        checkSeverity(25.0, 20.0, AlertSeverity.LOW); // Days: 25 / (20/14) = 17.5
        checkSeverity(35.0, 20.0, null); // Days: 35 / (20/14) = 24.5 -> OK -> Filtered
    }

    private void checkSeverity(double effectiveStock, double projected14Days, AlertSeverity expected) {
        // Reset mocks for each call if necessary, but here we can just mock a different
        // product each time
        // Or better, just one product and execute once per case
        Integer productId = 999;
        Product product = new Product();
        product.setId(productId);
        product.setName("Test Product");
        product.setCurrentStock(BigDecimal.valueOf(effectiveStock));
        product.setHidden(false);

        WeeklyIngredientConsumption row = mock(WeeklyIngredientConsumption.class);
        when(row.getProductId()).thenReturn(productId);
        when(row.getWeekIndex()).thenReturn(0);
        when(row.getTotalConsumed()).thenReturn(BigDecimal.valueOf(projected14Days / 2.0)); // arbitrary history

        when(cookingAuditRepository.findWeeklyConsumptionPerIngredient(any(), any())).thenReturn(List.of(row));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderDetailRepository.findPendingQuantityPerProduct()).thenReturn(List.of());
        when(forecaster.forecast(anyList(), anyInt(), anyInt())).thenReturn(projected14Days);

        List<StockAlertDTO> alerts = stockAlertService.getActiveAlerts();

        if (expected == null) {
            assertTrue(alerts.isEmpty(), "Should have no active alerts for effective stock " + effectiveStock);
        } else {
            assertFalse(alerts.isEmpty(), "Should have an alert for effective stock " + effectiveStock);
            assertEquals(expected, alerts.get(0).getSeverity());
        }
    }
}
