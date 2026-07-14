package com.economato.inventory.application.usecase.stock;

import com.economato.inventory.application.dto.stock.event.ForecastResultType;
import com.economato.inventory.application.dto.product.projection.PendingProductQuantity;
import com.economato.inventory.application.dto.shared.projection.WeeklyIngredientConsumption;
import com.economato.inventory.application.dto.stock.response.AlertResolution;
import com.economato.inventory.application.dto.stock.response.AlertSeverity;
import com.economato.inventory.application.dto.stock.response.AlertType;
import com.economato.inventory.application.dto.stock.response.StockAlertDTO;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.stock.StockPrediction;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockPredictionRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockThresholdEvaluator {

    private static final int DEFAULT_HISTORY_WEEKS = 12;
    private static final int DEFAULT_HORIZON_DAYS = 14;

    private final RecipeCookingAuditRepository cookingAuditRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final StockPredictionRepository predictionRepository;
    private final I18nService i18nService;
    private final ProductBatchService productBatchService;
    private final SystemConfigService systemConfigService;
    private final BatchExpirationMonitor batchExpirationMonitor;

    public List<StockAlertDTO> computeAlerts() {
        return computeAlerts(null);
    }

    public List<StockAlertDTO> computeAlerts(Set<Integer> filterIds) {
        LocalDateTime since = LocalDateTime.now().minusWeeks(getForecastHistoryWeeks());
        Map<Integer, BigDecimal> persistedPredictions = buildPredictionMap();

        if (filterIds != null && !filterIds.isEmpty()) {
            persistedPredictions.keySet().retainAll(filterIds);
        }

        Map<Integer, Product> productsById = buildProductMap();
        if (persistedPredictions.isEmpty()) {
            return batchExpirationMonitor.mergeExpirationAlerts(List.of(), filterIds, productsById);
        }

        Map<Integer, BigDecimal> pendingByProduct = buildPendingMap();
        Map<Integer, List<ProductBatch>> activeBatchesByProduct = buildActiveBatchesMap();

        List<Integer> productIdsToProcess = new ArrayList<>(persistedPredictions.keySet());
        List<Object[]> topRecipesData = cookingAuditRepository.findTopConsumingRecipesByProducts(productIdsToProcess, since);
        Map<Integer, List<String>> topRecipesByProduct = new HashMap<>();
        for (Object[] row : topRecipesData) {
            Integer pId = (Integer) row[0];
            String rName = (String) row[1];
            topRecipesByProduct.computeIfAbsent(pId, k -> new ArrayList<>()).add(rName);
        }

        List<StockAlertDTO> alerts = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : persistedPredictions.entrySet()) {
            Integer productId = entry.getKey();
            BigDecimal projected = entry.getValue();

            Product product = productsById.get(productId);
            if (product == null) continue;

            BigDecimal currentStock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
            BigDecimal pending = pendingByProduct.getOrDefault(productId, BigDecimal.ZERO);

            List<String> topRecipes = topRecipesByProduct.getOrDefault(productId, List.of());
            if (topRecipes.size() > 3) topRecipes = topRecipes.subList(0, 3);

            StockAlertDTO alert = buildAlert(productId, currentStock, pending, projected, activeBatchesByProduct, productsById, topRecipes);
            if (alert != null) {
                alerts.add(alert);
            }
        }

        return batchExpirationMonitor.mergeExpirationAlerts(alerts, filterIds, productsById);
    }

    public void updatePredictionFromForecast(Integer productId, BigDecimal projectedConsumption, LocalDateTime calculatedAt) {
        log.info("Actualizando predicción desde predictor externo para producto ID: {}. Nuevo valor: {}", 
                productId, projectedConsumption);
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND)));

        StockPrediction prediction = predictionRepository.findById(productId)
                .orElseGet(() -> StockPrediction.builder()
                        .product(product)
                        .build());

        prediction.setProjectedConsumption(projectedConsumption);
        prediction.setUpdatedAt(calculatedAt != null ? calculatedAt : LocalDateTime.now());
        predictionRepository.save(prediction);
        
        log.debug("Predicción actualizada con éxito para producto {}", productId);
    }

    public ForecastResultType classifyForecastResult(Integer productId, BigDecimal projectedConsumption) {
        if (productId == null || projectedConsumption == null || projectedConsumption.signum() < 0) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INVALID_OPERATION));
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND)));

        BigDecimal currentStock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
        if (projectedConsumption.signum() == 0) {
            return ForecastResultType.PREDICTION;
        }

        return projectedConsumption.compareTo(currentStock) > 0
                ? ForecastResultType.ALERT
                : ForecastResultType.PREDICTION;
    }

    private StockAlertDTO buildAlert(Integer productId,
                                     BigDecimal currentStock,
                                     BigDecimal pending,
                                     BigDecimal projected,
                                     Map<Integer, List<ProductBatch>> activeBatchesByProduct,
                                     Map<Integer, Product> productsById,
                                     List<String> topRecipes) {

        Product product = productsById.get(productId);
        if (product == null)
            return null;

        BigDecimal effective = currentStock.add(pending);
        BigDecimal gap = projected.subtract(effective).setScale(3, RoundingMode.HALF_UP);

        int daysRemaining;
        if (projected.compareTo(BigDecimal.ZERO) <= 0) {
            daysRemaining = Integer.MAX_VALUE; 
        } else {
            BigDecimal dailyRate = projected.divide(BigDecimal.valueOf(getForecastHorizonDays()), 6, RoundingMode.HALF_UP);
            if (dailyRate.compareTo(BigDecimal.ZERO) == 0) {
                daysRemaining = Integer.MAX_VALUE;
            } else {
                List<ProductBatch> batches = activeBatchesByProduct.getOrDefault(productId, List.of());
                daysRemaining = calculateDaysRemainingWithExpiration(dailyRate, batches, pending);
            }
        }

        AlertSeverity severity = classifySeverity(daysRemaining);
        if (severity == AlertSeverity.OK)
            return null; 

        AlertResolution resolution = classifyResolution(gap, pending);
        String message = buildMessage(product.getName(), currentStock, pending, projected, gap,
                resolution, product.getUnit());

        return StockAlertDTO.builder()
                .productId(productId)
                .productName(product.getName())
                .unit(product.getUnit())
                .lotQuantity(product.getLotQuantity())
                .currentStock(currentStock)
                .pendingOrderQuantity(pending)
                .projectedConsumption(projected)
                .effectiveGap(gap)
                .estimatedDaysRemaining(Math.min(daysRemaining, 999))
                .severity(severity)
                .alertType(AlertType.PREDICTION)
                .resolution(resolution)
                .message(message)
                .nearestExpirationDate(null)
                .expiringQuantity(BigDecimal.ZERO)
                .topConsumingRecipes(topRecipes)
                .build();
    }

    private AlertSeverity classifySeverity(int days) {
        Thresholds t = getAlertThresholdsOrDefault();
        if (days >= t.alertThresholdOkDays())
            return AlertSeverity.OK;
        if (days >= t.alertThresholdLowDays())
            return AlertSeverity.LOW;
        if (days >= t.alertThresholdMediumDays())
            return AlertSeverity.MEDIUM;
        if (days >= t.alertThresholdHighDays())
            return AlertSeverity.HIGH;
        return AlertSeverity.CRITICAL;
    }

    private AlertResolution classifyResolution(BigDecimal gap, BigDecimal pending) {
        if (gap.compareTo(BigDecimal.ZERO) <= 0) {
            return AlertResolution.OK; 
        }
        if (pending.compareTo(BigDecimal.ZERO) <= 0) {
            return AlertResolution.UNCOVERED;
        }
        return AlertResolution.PARTIALLY_COVERED;
    }

    private String buildMessage(String name, BigDecimal stock, BigDecimal pending,
                               BigDecimal projected, BigDecimal gap,
                               AlertResolution resolution, String unit) {

        String gapStr = gap.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String projStr = projected.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String stockStr = stock.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String pendStr = pending.setScale(2, RoundingMode.HALF_UP).toPlainString();

        Object[] args;
        MessageKey key;

        switch (resolution) {
            case COVERED_BY_ORDER -> {
                key = MessageKey.STOCK_ALERT_MESSAGE_COVERED;
                args = new Object[] { name, stockStr, unit, getForecastHorizonDays(), projStr, unit, pendStr, unit };
            }
            case PARTIALLY_COVERED -> {
                key = MessageKey.STOCK_ALERT_MESSAGE_PARTIALLY_COVERED;
                args = new Object[] { name, stockStr, unit, getForecastHorizonDays(), projStr, unit, pendStr, unit, gapStr, unit };
            }
            case UNCOVERED -> {
                key = MessageKey.STOCK_ALERT_MESSAGE_UNCOVERED;
                args = new Object[] { name, stockStr, unit, getForecastHorizonDays(), projStr, unit, gapStr, unit };
            }
            default -> {
                key = MessageKey.STOCK_ALERT_MESSAGE_DEFAULT;
                args = new Object[] { name, gapStr, unit };
            }
        }

        return i18nService.getMessage(key, args);
    }

    public Map<Integer, BigDecimal> buildPredictionMap() {
        return predictionRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        StockPrediction::getId,
                        StockPrediction::getProjectedConsumption));
    }

    public Map<Integer, Product> buildProductMap() {
        return productRepository.findAllActive().stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
    }

    public Map<Integer, BigDecimal> buildPendingMap() {
        return orderDetailRepository.findPendingQuantityPerProduct()
                .stream()
                .collect(Collectors.toMap(
                        PendingProductQuantity::getProductId,
                        p -> p.getPendingQuantity() != null ? p.getPendingQuantity() : BigDecimal.ZERO));
    }

    public Map<Integer, List<ProductBatch>> buildActiveBatchesMap() {
        return productBatchService.getAllActiveBatches().stream()
                .collect(Collectors.groupingBy(b -> b.getProduct().getId()));
    }

    private int calculateDaysRemainingWithExpiration(BigDecimal dailyRate, List<ProductBatch> batches, BigDecimal pending) {
        if (dailyRate.compareTo(BigDecimal.ZERO) == 0) return Integer.MAX_VALUE;

        List<ProductBatch> simulatedBatches = new ArrayList<>();
        for (ProductBatch b : batches) {
            ProductBatch clone = ProductBatch.builder()
                    .expirationDate(b.getExpirationDate())
                    .remainingQuantity(b.getRemainingQuantity())
                    .build();
            simulatedBatches.add(clone);
        }

        BigDecimal simulatedPending = pending;
        LocalDate simulatedDate = LocalDate.now();

        for (int day = 0; day <= 999; day++) {
            final LocalDate currentDate = simulatedDate.plusDays(day);

            simulatedBatches.removeIf(b -> b.getExpirationDate() != null && !b.getExpirationDate().isAfter(currentDate));

            BigDecimal currentDayDemand = dailyRate;

            for (ProductBatch b : simulatedBatches) {
                if (currentDayDemand.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal available = b.getRemainingQuantity();
                BigDecimal toConsume = available.min(currentDayDemand);
                b.setRemainingQuantity(available.subtract(toConsume));
                currentDayDemand = currentDayDemand.subtract(toConsume);
            }

            simulatedBatches.removeIf(b -> b.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0);

            if (currentDayDemand.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal toConsume = simulatedPending.min(currentDayDemand);
                simulatedPending = simulatedPending.subtract(toConsume);
                currentDayDemand = currentDayDemand.subtract(toConsume);
            }

            if (currentDayDemand.compareTo(BigDecimal.valueOf(0.01)) > 0) {
                return day;
            }
        }

        return 999;
    }

    public int getForecastHistoryWeeks() {
        if (systemConfigService == null) {
            return DEFAULT_HISTORY_WEEKS;
        }
        try {
            return systemConfigService.getConfigEntity().getForecastHistoryWeeks();
        } catch (Exception ignored) {
            return DEFAULT_HISTORY_WEEKS;
        }
    }

    public int getForecastHorizonDays() {
        if (systemConfigService == null) {
            return DEFAULT_HORIZON_DAYS;
        }
        try {
            return systemConfigService.getConfigEntity().getForecastHorizonDays();
        } catch (Exception ignored) {
            return DEFAULT_HORIZON_DAYS;
        }
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
