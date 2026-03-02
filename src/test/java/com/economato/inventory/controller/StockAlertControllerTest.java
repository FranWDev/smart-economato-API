package com.economato.inventory.controller;

import com.economato.inventory.dto.response.AlertResolution;
import com.economato.inventory.dto.response.AlertSeverity;
import com.economato.inventory.dto.response.StockAlertDTO;
import com.economato.inventory.service.StockAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAlertControllerTest {

    @Mock
    private StockAlertService stockAlertService;

    @InjectMocks
    private StockAlertController controller;

    private StockAlertDTO criticalAlert() {
        return StockAlertDTO.builder()
                .productId(1)
                .productName("Harina de trigo")
                .unit("kg")
                .currentStock(BigDecimal.valueOf(0.5))
                .pendingOrderQuantity(BigDecimal.ZERO)
                .projectedConsumption(BigDecimal.valueOf(8.0))
                .effectiveGap(BigDecimal.valueOf(7.5))
                .estimatedDaysRemaining(1)
                .severity(AlertSeverity.CRITICAL)
                .resolution(AlertResolution.UNCOVERED)
                .message("Harina de trigo — Stock insuficiente. Sin pedidos activos.")
                .topConsumingRecipes(List.of("Pan artesano", "Pizza"))
                .build();
    }

    @Test
    void getAlerts_withoutFilter_returnsAllActiveAlerts() {
        when(stockAlertService.getActiveAlerts()).thenReturn(List.of(criticalAlert()));

        ResponseEntity<List<StockAlertDTO>> response = controller.getAlerts(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(AlertSeverity.CRITICAL, response.getBody().get(0).getSeverity());
        verify(stockAlertService).getActiveAlerts();
        verifyNoMoreInteractions(stockAlertService);
    }

    @Test
    void getAlerts_withSeverityFilter_delegatesToFilteredMethod() {
        when(stockAlertService.getAlertsBySeverity(AlertSeverity.HIGH)).thenReturn(List.of(criticalAlert()));

        ResponseEntity<List<StockAlertDTO>> response = controller.getAlerts(AlertSeverity.HIGH);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
        verify(stockAlertService).getAlertsBySeverity(AlertSeverity.HIGH);
        verifyNoMoreInteractions(stockAlertService);
    }

    @Test
    void getAlerts_whenNoAlerts_returnsEmptyList() {
        when(stockAlertService.getActiveAlerts()).thenReturn(List.of());

        ResponseEntity<List<StockAlertDTO>> response = controller.getAlerts(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}
