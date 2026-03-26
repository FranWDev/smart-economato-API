package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledForecastRefreshService {

    private final ProductRepository productRepository;
    private final StockLedgerRepository stockLedgerRepository;
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
            List<Integer> batch = activeMovedProductIds.subList(i, end);
            
            // Llamada al método anotado para que el aspecto lo capture
            triggerRefresh(new ArrayList<>(batch));
        }
        
        // Notificar a administradores por WebSocket
        webSocketNotificationService.notifyAdminsStockPrediction(activeMovedProductIds.size());
        
        log.info("Refresco programado completado para {} productos en {} lotes.", 
                activeMovedProductIds.size(), (int) Math.ceil((double) activeMovedProductIds.size() / batchSize));
    }

    /**
     * Helper method to trigger the PredictorTriggerAspect.
     * Needs to return the list of IDs for the aspect to extract them from the result.
     */
    @PredictorTrigger(action = "SCHEDULED_REFRESH")
    public List<Integer> triggerRefresh(List<Integer> batch) {
        log.debug("Disparando actualización para lote de {} productos", batch.size());
        return batch;
    }
}
