package com.economato.inventory.application.usecase.stock;

import com.economato.inventory.application.dto.stock.response.AlertResolution;
import com.economato.inventory.application.dto.stock.response.AlertSeverity;
import com.economato.inventory.application.dto.stock.response.AlertType;
import com.economato.inventory.application.dto.stock.response.StockAlertDTO;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BatchExpirationMonitor {

    private final ProductBatchService productBatchService;
    private final I18nService i18nService;
    private final SystemConfigService systemConfigService;

    public List<StockAlertDTO> mergeExpirationAlerts(List<StockAlertDTO> baseAlerts, Set<Integer> filterIds, Map<Integer, Product> productsById) {
        List<ProductBatch> expiringBatches = productBatchService.getExpiringBatches(7);
        if (filterIds != null && !filterIds.isEmpty()) {
            expiringBatches = expiringBatches.stream()
                    .filter(batch -> filterIds.contains(batch.getProduct().getId()))
                    .toList();
        }

        if (expiringBatches.isEmpty()) {
            return baseAlerts;
        }

        Map<Integer, List<ProductBatch>> expiringByProduct = expiringBatches.stream()
                .collect(Collectors.groupingBy(batch -> batch.getProduct().getId()));

        Map<Integer, StockAlertDTO> alertsByProduct = baseAlerts.stream()
                .collect(Collectors.toMap(StockAlertDTO::getProductId, alert -> alert, (left, right) -> left));

        for (Map.Entry<Integer, List<ProductBatch>> entry : expiringByProduct.entrySet()) {
            Integer productId = entry.getKey();
            List<ProductBatch> batches = entry.getValue();

            LocalDate nearestExpiration = batches.stream()
                    .map(ProductBatch::getExpirationDate)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);

            if (nearestExpiration == null) {
                continue;
            }

            BigDecimal expiringQuantity = batches.stream()
                    .map(ProductBatch::getRemainingQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long daysToExpire = ChronoUnit.DAYS.between(LocalDate.now(), nearestExpiration);
            AlertSeverity expirationSeverity = classifyExpirationSeverity(daysToExpire);

            String expiringMessage = i18nService.getMessage(
                    MessageKey.STOCK_ALERT_MESSAGE_EXPIRING,
                    new Object[] { nearestExpiration, expiringQuantity.setScale(3, RoundingMode.HALF_UP) });

            StockAlertDTO existing = alertsByProduct.get(productId);
            if (existing == null) {
                Product product = productsById.get(productId);
                if (product == null) {
                    continue;
                }

                StockAlertDTO expirationOnly = StockAlertDTO.builder()
                        .productId(productId)
                        .productName(product.getName())
                        .unit(product.getUnit())
                        .lotQuantity(product.getLotQuantity())
                        .currentStock(product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO)
                        .pendingOrderQuantity(BigDecimal.ZERO)
                        .projectedConsumption(BigDecimal.ZERO)
                        .effectiveGap(BigDecimal.ZERO)
                        .estimatedDaysRemaining(Math.max((int) daysToExpire, 0))
                        .severity(expirationSeverity)
                        .resolution(AlertResolution.EXPIRING)
                        .alertType(AlertType.EXPIRATION)
                        .message(expiringMessage)
                        .nearestExpirationDate(nearestExpiration)
                        .expiringQuantity(expiringQuantity)
                        .topConsumingRecipes(List.of())
                        .build();
                alertsByProduct.put(productId, expirationOnly);
                continue;
            }

            AlertSeverity mergedSeverity = expirationSeverity.ordinal() > existing.getSeverity().ordinal()
                    ? expirationSeverity
                    : existing.getSeverity();

            StockAlertDTO merged = StockAlertDTO.builder()
                    .productId(existing.getProductId())
                    .productName(existing.getProductName())
                    .unit(existing.getUnit())
                    .lotQuantity(existing.getLotQuantity())
                    .currentStock(existing.getCurrentStock())
                    .pendingOrderQuantity(existing.getPendingOrderQuantity())
                    .projectedConsumption(existing.getProjectedConsumption())
                    .effectiveGap(existing.getEffectiveGap())
                    .estimatedDaysRemaining(existing.getEstimatedDaysRemaining())
                    .severity(mergedSeverity)
                    .resolution(existing.getResolution())
                    .alertType(AlertType.COMBINED)
                    .message(existing.getMessage() + " " + expiringMessage)
                    .nearestExpirationDate(nearestExpiration)
                    .expiringQuantity(expiringQuantity)
                    .topConsumingRecipes(existing.getTopConsumingRecipes())
                    .build();

            alertsByProduct.put(productId, merged);
        }

        return new ArrayList<>(alertsByProduct.values());
    }

    private AlertSeverity classifyExpirationSeverity(long daysToExpire) {
        Thresholds t = getAlertThresholdsOrDefault();
        if (daysToExpire < t.expirationCriticalDays()) {
            return AlertSeverity.CRITICAL;
        }
        if (daysToExpire < t.expirationHighDays()) {
            return AlertSeverity.HIGH;
        }
        if (daysToExpire < t.expirationMediumDays()) {
            return AlertSeverity.MEDIUM;
        }
        return AlertSeverity.LOW;
    }

    private Thresholds getAlertThresholdsOrDefault() {
        if (systemConfigService == null) {
            return new Thresholds(21, 14, 7, 3, 3, 7, 14);
        }
        try {
            var cfg = systemConfigService.getAlertThresholds();
            return new Thresholds(
                    cfg.alertThresholdOkDays(),
                    cfg.alertThresholdLowDays(),
                    cfg.alertThresholdMediumDays(),
                    cfg.alertThresholdHighDays(),
                    cfg.expirationCriticalDays(),
                    cfg.expirationHighDays(),
                    cfg.expirationMediumDays());
        } catch (Exception ignored) {
            return new Thresholds(21, 14, 7, 3, 3, 7, 14);
        }
    }

    private record Thresholds(int alertThresholdOkDays,
                              int alertThresholdLowDays,
                              int alertThresholdMediumDays,
                              int alertThresholdHighDays,
                              int expirationCriticalDays,
                              int expirationHighDays,
                              int expirationMediumDays) {
    }
}
