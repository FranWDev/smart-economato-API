package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledForecastRefreshService {

    private final ProductRepository productRepository;

    /**
     * Refresh all active product forecasts every 6 hours.
     * Uses batches of 20 to avoid large message payloads and database load.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional(readOnly = true)
    public void scheduleForecastRefresh() {
        log.info("Iniciando refresco programado de predicciones...");
        
        List<Product> activeProducts = productRepository.findAllActive();
        if (activeProducts.isEmpty()) {
            log.info("No hay productos activos para refrescar.");
            return;
        }

        List<Integer> productIds = activeProducts.stream()
                .map(Product::getId)
                .toList();

        int batchSize = 20;
        for (int i = 0; i < productIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, productIds.size());
            List<Integer> batch = productIds.subList(i, end);
            
            // Llamada al método anotado para que el aspecto lo capture
            triggerRefresh(new ArrayList<>(batch));
        }
        
        log.info("Refresco programado completado para {} productos en {} lotes.", 
                productIds.size(), (int) Math.ceil((double) productIds.size() / batchSize));
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
