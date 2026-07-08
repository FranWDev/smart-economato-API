package com.economato.inventory.application.usecase.stock;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.WebSocketNotificationService;

import com.economato.inventory.application.dto.stock.event.StockPredictionEvent;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private StockLedgerService stockLedgerService;

    @Mock
    private AuditEventProducer auditEventProducer;

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    @Mock
    private PersistentNotificationService persistentNotificationService;

    private ScheduledForecastRefreshService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledForecastRefreshService(
                productRepository, 
                stockLedgerRepository, 
                stockLedgerService, 
                auditEventProducer, 
                webSocketNotificationService,
                persistentNotificationService
        );
    }

    @Test
    @DisplayName("Should refresh only active products that had movements in the last 6 hours")
    void shouldRefreshOnlyMovedAndActiveProducts() {
        // given
        Product p1 = new Product(); p1.setId(1); // Active and moved
        Product p2 = new Product(); p2.setId(2); // Active but NOT moved

        given(stockLedgerRepository.findProductIdsWithMovementsSince(any())).willReturn(List.of(1, 3));
        given(productRepository.findAllActive()).willReturn(List.of(p1, p2));
        given(stockLedgerService.getDailyConsumptionBatch(any(), any(), any()))
                .willReturn(Map.of(1, Collections.emptyList()));

        // when
        service.scheduleForecastRefresh();

        // then
        verify(auditEventProducer, times(1)).publishStockPredictionEvent(any(StockPredictionEvent.class));
        verify(webSocketNotificationService, times(1)).notifyAdminsStockPrediction(1);
        verify(persistentNotificationService, times(1)).notifyStockPrediction(1);
    }

    @Test
    @DisplayName("Should process in batches of 20")
    void shouldProcessInBatches() {
        // given
        List<Integer> movedIds = new ArrayList<>();
        List<Product> activeProducts = new ArrayList<>();
        Map<Integer, List<DailyConsumptionDTO>> mockConsumptions = new HashMap<>();
        
        for (int i = 1; i <= 25; i++) {
            movedIds.add(i);
            Product p = new Product(); p.setId(i);
            activeProducts.add(p);
            mockConsumptions.put(i, Collections.emptyList());
        }

        given(stockLedgerRepository.findProductIdsWithMovementsSince(any())).willReturn(movedIds);
        given(productRepository.findAllActive()).willReturn(activeProducts);
        given(stockLedgerService.getDailyConsumptionBatch(any(), any(), any())).willReturn(mockConsumptions);

        // when
        service.scheduleForecastRefresh();

        // then
        // 25 products / 20 batch size = 2 events
        ArgumentCaptor<StockPredictionEvent> eventCaptor = ArgumentCaptor.forClass(StockPredictionEvent.class);
        verify(auditEventProducer, times(2)).publishStockPredictionEvent(eventCaptor.capture());
        
        List<StockPredictionEvent> events = eventCaptor.getAllValues();
        assertEquals(20, events.get(0).getAffectedProductIds().size());
        assertEquals(5, events.get(1).getAffectedProductIds().size());
        
        verify(webSocketNotificationService).notifyAdminsStockPrediction(25);
        verify(persistentNotificationService).notifyStockPrediction(25);
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
        verify(persistentNotificationService, never()).notifyStockPrediction(anyInt());
        verify(productRepository, never()).findAllActive();
    }
}
