package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.Product;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledForecastRefreshServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockLedgerRepository stockLedgerRepository;

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    private ScheduledForecastRefreshService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledForecastRefreshService(productRepository, stockLedgerRepository, webSocketNotificationService);
    }

    @Test
    @DisplayName("Should refresh only active products that had movements in the last 6 hours")
    void shouldRefreshOnlyMovedAndActiveProducts() {
        // given
        Product p1 = new Product(); p1.setId(1); // Active and moved
        Product p2 = new Product(); p2.setId(2); // Active but NOT moved
        Product p3 = new Product(); p3.setId(3); // Moved but NOT active

        given(stockLedgerRepository.findProductIdsWithMovementsSince(any())).willReturn(List.of(1, 3));
        given(productRepository.findAllActive()).willReturn(List.of(p1, p2));

        // when
        service.scheduleForecastRefresh();

        // then
        // Only productId 1 should be refreshed (it's the only one that is both active and moved)
        verify(webSocketNotificationService, times(1)).notifyAdminsStockPrediction(1);
    }

    @Test
    @DisplayName("Should not send notification if no products had movements")
    void shouldNotSendNotificationWhenNoMovements() {
        // given
        given(stockLedgerRepository.findProductIdsWithMovementsSince(any())).willReturn(List.of());

        // when
        service.scheduleForecastRefresh();

        // then
        verify(webSocketNotificationService, never()).notifyAdminsStockPrediction(anyInt());
        verify(productRepository, never()).findAllActive();
    }
}
