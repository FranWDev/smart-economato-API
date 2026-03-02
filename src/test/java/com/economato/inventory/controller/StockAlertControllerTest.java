package com.economato.inventory.controller;

import com.economato.inventory.dto.response.AlertResolution;
import com.economato.inventory.dto.response.AlertSeverity;
import com.economato.inventory.dto.response.StockAlertDTO;
import com.economato.inventory.dto.response.StockPredictionResponseDTO;
import com.economato.inventory.service.StockAlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Test
    void getProductAlert_whenAlertExists_returnsOk() {
        StockAlertDTO alert = criticalAlert();
        when(stockAlertService.getAlertByProductId(1)).thenReturn(java.util.Optional.of(alert));

        ResponseEntity<StockAlertDTO> response = controller.getProductAlert(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(alert, response.getBody());
    }

    @Test
    void getProductAlert_whenNoAlert_returnsNoContent() {
        when(stockAlertService.getAlertByProductId(99)).thenReturn(java.util.Optional.empty());

        ResponseEntity<StockAlertDTO> response = controller.getProductAlert(99);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void getBatchAlerts_returnsAlertList() {
        StockAlertDTO alert = criticalAlert();
        List<Integer> ids = List.of(1, 2);
        when(stockAlertService.getAlertsByProductIds(ids)).thenReturn(List.of(alert));

        ResponseEntity<List<StockAlertDTO>> response = controller.getBatchAlerts(ids);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(alert, response.getBody().get(0));
    }

    @Test
    void getPredictions_returnsPaginatedPredictions() {
        StockPredictionResponseDTO dto = StockPredictionResponseDTO.builder()
                .productId(1)
                .productName("Harina")
                .projectedConsumption(BigDecimal.valueOf(10.0))
                .build();
        Page<StockPredictionResponseDTO> page = new PageImpl<>(List.of(dto));
        Pageable pageable = PageRequest.of(0, 10);

        when(stockAlertService.getAllPredictions(pageable)).thenReturn(page);

        ResponseEntity<Page<StockPredictionResponseDTO>> response = controller.getPredictions(pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(page, response.getBody());
        verify(stockAlertService).getAllPredictions(pageable);
    }
}
