package com.economato.inventory.application.usecase.stock;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.WebSocketNotificationService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.stock.event.StockPredictionEvent;
import com.economato.inventory.application.dto.stock.event.StockPredictionEvent.DailyConsumption;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Profile({ "!test", "kafka-test" })
@Slf4j
public class ScheduledForecastRefreshService {

    private static final int DEFAULT_INTERVAL_HOURS = 6;
    private static final int DEFAULT_BATCH_SIZE = 20;
    private static final int DEFAULT_HISTORY_DAYS = 90;

    private final ProductRepository productRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final StockLedgerService stockLedgerService;
    private final AuditEventProducer auditEventProducer;
    private final WebSocketNotificationService webSocketNotificationService;
    private final PersistentNotificationService persistentNotificationService;
    private final SystemConfigService systemConfigService;

    @Autowired
    public ScheduledForecastRefreshService(ProductRepository productRepository,
            StockLedgerRepository stockLedgerRepository,
            StockLedgerService stockLedgerService,
            AuditEventProducer auditEventProducer,
            WebSocketNotificationService webSocketNotificationService,
            PersistentNotificationService persistentNotificationService,
            @Autowired(required = false) SystemConfigService systemConfigService) {
        this.productRepository = productRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.stockLedgerService = stockLedgerService;
        this.auditEventProducer = auditEventProducer;
        this.webSocketNotificationService = webSocketNotificationService;
        this.persistentNotificationService = persistentNotificationService;
        this.systemConfigService = systemConfigService;
    }

    public ScheduledForecastRefreshService(ProductRepository productRepository,
            StockLedgerRepository stockLedgerRepository,
            StockLedgerService stockLedgerService,
            AuditEventProducer auditEventProducer,
            WebSocketNotificationService webSocketNotificationService,
            PersistentNotificationService persistentNotificationService) {
        this(productRepository, stockLedgerRepository, stockLedgerService, auditEventProducer,
                webSocketNotificationService, persistentNotificationService, null);
    }

    /**
     * Refresh forecasts for products with stock alterations in the last 6 hours.
     * Uses batches of 20 to avoid large message payloads and database load.
     */
    @Transactional
    public void scheduleForecastRefresh() {
        log.info("Iniciando refresco programado de predicciones...");

        if (systemConfigService != null) {
            try {
                if (!systemConfigService.getPredictionConfig().predictionRefreshEnabled()) {
                    log.debug("Refresco de predicciones deshabilitado por configuración");
                    return;
                }
            } catch (Exception ignored) {
                // fallback
            }
        }

        int intervalHours = getRefreshIntervalHours();
        LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(intervalHours);
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

        int batchSize = getBatchSize();
        for (int i = 0; i < activeMovedProductIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, activeMovedProductIds.size());
            List<Integer> batch = new ArrayList<>(activeMovedProductIds.subList(i, end));
            
            publishPredictionEvent(batch);
        }
        
        // Notificar a administradores por WebSocket
        webSocketNotificationService.notifyAdminsStockPrediction(activeMovedProductIds.size());
        persistentNotificationService.notifyStockPrediction(activeMovedProductIds.size());
        
        log.info("Refresco programado completado para {} productos en {} lotes.", 
                activeMovedProductIds.size(), (int) Math.ceil((double) activeMovedProductIds.size() / batchSize));
    }

    private void publishPredictionEvent(List<Integer> productIds) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(getHistoryDays()).withHour(0).withMinute(0).withSecond(0).withNano(0);

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

    public int getRefreshIntervalHours() {
        if (systemConfigService == null) {
            return DEFAULT_INTERVAL_HOURS;
        }
        try {
            return Math.max(1, systemConfigService.getPredictionConfig().predictionRefreshIntervalHours());
        } catch (Exception ignored) {
            return DEFAULT_INTERVAL_HOURS;
        }
    }

    private int getBatchSize() {
        if (systemConfigService == null) {
            return DEFAULT_BATCH_SIZE;
        }
        try {
            return Math.max(1, systemConfigService.getPredictionConfig().predictionBatchSize());
        } catch (Exception ignored) {
            return DEFAULT_BATCH_SIZE;
        }
    }

    private int getHistoryDays() {
        if (systemConfigService == null) {
            return DEFAULT_HISTORY_DAYS;
        }
        try {
            return Math.max(1, systemConfigService.getPredictionConfig().predictionHistoryDays());
        } catch (Exception ignored) {
            return DEFAULT_HISTORY_DAYS;
        }
    }
}
