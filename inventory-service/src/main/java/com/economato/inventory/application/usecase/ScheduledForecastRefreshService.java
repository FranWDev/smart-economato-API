package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.event.StockPredictionEvent;
import com.economato.inventory.application.dto.event.StockPredictionEvent.DailyConsumption;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledForecastRefreshService {

    private final ProductRepository productRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final StockLedgerService stockLedgerService;
    private final AuditEventProducer auditEventProducer;
    private final WebSocketNotificationService webSocketNotificationService;

    /**
     * Refresh forecasts for products with stock alterations in the last 6 hours.
     * Uses batches of 20 to avoid large message payloads and database load.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional(readOnly = true)
    public void scheduleForecastRefresh() {
        log.info("Iniciando refresco programado de predicciones...");

        LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(6);
        List<Integer> movedProductIds = stockLedgerRepository.findProductIdsWithMovementsSince(sixHoursAgo);

        if (movedProductIds.isEmpty()) {
            log.info("No hay alteraciones de stock en las últimas 6 horas. Saltando refresco.");
            return;
        }

        // De los productos con movimientos, solo refrescamos los que siguen activos
        List<Integer> activeMovedProductIds = productRepository.findAllActive().stream()
                .map(Product::getId)
                .filter(movedProductIds::contains)
                .toList();

        if (activeMovedProductIds.isEmpty()) {
            log.info("Los productos con movimientos no están activos actualmente.");
            return;
        }

        int batchSize = 20;
        for (int i = 0; i < activeMovedProductIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, activeMovedProductIds.size());
            List<Integer> batch = new ArrayList<>(activeMovedProductIds.subList(i, end));
            
            publishPredictionEvent(batch);
        }
        
        // Notificar a administradores por WebSocket
        webSocketNotificationService.notifyAdminsStockPrediction(activeMovedProductIds.size());
        
        log.info("Refresco programado completado para {} productos en {} lotes.", 
                activeMovedProductIds.size(), (int) Math.ceil((double) activeMovedProductIds.size() / batchSize));
    }

    private void publishPredictionEvent(List<Integer> productIds) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(90).withHour(0).withMinute(0).withSecond(0).withNano(0);

        Map<Integer, List<DailyConsumptionDTO>> batchResults =
                stockLedgerService.getDailyConsumptionBatch(productIds, start, end);

        Map<Integer, List<DailyConsumption>> productHistories = new HashMap<>();
        for (Integer pid : productIds) {
            List<DailyConsumptionDTO> breakdown = batchResults.get(pid);
            if (breakdown != null) {
                productHistories.put(pid, breakdown.stream()
                        .map(d -> DailyConsumption.builder()
                                .date(d.getDate())
                                .consumed(d.getConsumed())
                                .build())
                        .collect(Collectors.toList()));
            } else {
                productHistories.put(pid, Collections.emptyList());
            }
        }

        StockPredictionEvent event = StockPredictionEvent.builder()
                .triggerType("SCHEDULED_REFRESH")
                .affectedProductIds(productIds)
                .productHistories(productHistories)
                .timestamp(LocalDateTime.now())
                .userName("Sistema")
                .build();

        auditEventProducer.publishStockPredictionEvent(event);
    }
}
