package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.event.ForecastResultType;
import com.economato.inventory.application.dto.projection.PendingProductQuantity;
import com.economato.inventory.application.dto.projection.WeeklyIngredientConsumption;
import com.economato.inventory.application.dto.response.AlertResolution;
import com.economato.inventory.application.dto.response.AlertSeverity;
import com.economato.inventory.application.dto.response.DailyForecastResponseDTO;
import com.economato.inventory.application.dto.response.StockAlertDTO;
import com.economato.inventory.application.dto.response.StockPredictionResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyConsumptionResponseDTO;
import com.economato.inventory.application.mapper.StockDailyForecastMapper;
import com.economato.inventory.application.mapper.StockWeeklyConsumptionHistoryMapper;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.StockDailyForecast;
import com.economato.inventory.domain.model.StockPrediction;
import com.economato.inventory.domain.model.StockWeeklyConsumptionHistory;
import com.economato.inventory.infrastructure.adapter.out.external.prediction.HoltWintersForecaster;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockDailyForecastRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockPredictionRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockWeeklyConsumptionHistoryRepository;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Genera alertas predictivas de stock bajo combinando:
 * Proyección Holt-Winters del consumo de ingredientes (12 semanas históricas).
 * Stock físico actual ({@code product.currentStock}).
 * Cantidades pendientes de recibir en pedidos activos (CREATED / PENDING /
 * REVIEW).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAlertService {

    private static final int HISTORY_WEEKS = 12;

    private static final int HORIZON_DAYS = 14;

    private static final int SEASON_PERIOD = 1;

    private final RecipeCookingAuditRepository cookingAuditRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final StockPredictionRepository predictionRepository;
    private final StockDailyForecastRepository dailyForecastRepository;
    private final StockWeeklyConsumptionHistoryRepository weeklyHistoryRepository;
    private final StockDailyForecastMapper stockDailyForecastMapper;
    private final StockWeeklyConsumptionHistoryMapper stockWeeklyConsumptionHistoryMapper;
    private final HoltWintersForecaster forecaster;
    private final MessageSource messageSource;
    private final ProductBatchService productBatchService;

    /**
     * Calcula y devuelve todas las alertas predictivas activas
     * (es decir, severidad distinta de {@code OK}).
     *
     * @return lista de alertas ordenada por severidad descendente (CRITICAL
     *         primero)
     */
    @Transactional(readOnly = true)
    public List<StockAlertDTO> getActiveAlerts() {
        return computeAlerts().stream()
                .filter(a -> a.getSeverity() != AlertSeverity.OK)
                .sorted(Comparator.comparing(StockAlertDTO::getSeverity).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Devuelve las alertas filtradas por nivel de severidad mínimo.
     */
    @Transactional(readOnly = true)
    public List<StockAlertDTO> getAlertsBySeverity(AlertSeverity minSeverity) {
        return getActiveAlerts().stream()
                .filter(a -> a.getSeverity().ordinal() >= minSeverity.ordinal())
                .collect(Collectors.toList());
    }

    /**
     * Devuelve la alerta predictiva para un producto específico, si existe.
     */
    @Transactional(readOnly = true)
    public Optional<StockAlertDTO> getAlertByProductId(Integer productId) {
        return computeAlerts(Set.of(productId)).stream().findFirst();
    }

    /**
     * Devuelve las alertas predictivas para una lista específica de productos.
     */
    @Transactional(readOnly = true)
    public List<StockAlertDTO> getAlertsByProductIds(Collection<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return computeAlerts(new HashSet<>(productIds));
    }

    // -------------------------------------------------------------------------
    // Lógica de cálculo
    // -------------------------------------------------------------------------

    /**
     * Devuelve una lista paginada de todas las predicciones almacenadas.
     */
    @Transactional(readOnly = true)
    public Page<StockPredictionResponseDTO> getAllPredictions(Pageable pageable) {
        return predictionRepository.findAll(pageable)
                .map(prediction -> StockPredictionResponseDTO.builder()
                        .productId(prediction.getId())
                        .productName(prediction.getProduct().getName())
                        .projectedConsumption(prediction.getProjectedConsumption())
                        .projectedConsumptionUnit(prediction.getProduct().getUnit())
                        .currentStock(prediction.getProduct().getCurrentStock())
                        .updatedAt(prediction.getUpdatedAt())
                        .build());
    }

    /**
     * Devuelve el historial semanal de consumo para un producto concreto
     * (si se indica {@code productId}) o para todos los productos con historial
     * (si {@code productId} es {@code null}).
     */
    @Transactional(readOnly = true)
    public List<WeeklyConsumptionResponseDTO> getWeeklyConsumptionHistory(Integer productId) {
        if (productId == null) {
            return getWeeklyConsumptionHistoryAll();
        }
        return weeklyHistoryRepository.findOneById(productId)
            .map(stockWeeklyConsumptionHistoryMapper::toDTO)
            .map(List::of)
            .orElseGet(List::of);
    }

    /**
     * Devuelve el historial semanal de consumo para todos los productos con
     * historial en el período analizado.
     */
    @Transactional(readOnly = true)
    public List<WeeklyConsumptionResponseDTO> getWeeklyConsumptionHistoryAll() {
        return weeklyHistoryRepository.findAll().stream()
                .sorted(Comparator.comparing(StockWeeklyConsumptionHistory::getId))
                .map(stockWeeklyConsumptionHistoryMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Devuelve el historial semanal de consumo para todos los productos en formato
     * paginado.
     */
    @Transactional(readOnly = true)
    public Page<WeeklyConsumptionResponseDTO> getWeeklyConsumptionHistoryAll(Pageable pageable) {
        return weeklyHistoryRepository.findAll(pageable)
                .map(stockWeeklyConsumptionHistoryMapper::toDTO);
    }

        /**
         * Devuelve la proyección diaria de consumo para un producto concreto.
         */
    @Transactional(readOnly = true)
    public Optional<DailyForecastResponseDTO> getDailyForecast(Integer productId) {
        return dailyForecastRepository.findOneById(productId)
                .map(stockDailyForecastMapper::toDTO);
    }

            /**
             * Devuelve la proyección diaria de consumo para todos los productos con
             * historial en el período analizado.
             */
    @Transactional(readOnly = true)
    public List<DailyForecastResponseDTO> getDailyForecastAll() {
        return dailyForecastRepository.findAll().stream()
                .sorted(Comparator.comparing(StockDailyForecast::getId))
                .map(stockDailyForecastMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Devuelve la proyección diaria de consumo en formato paginado.
     */
    @Transactional(readOnly = true)
    public Page<DailyForecastResponseDTO> getDailyForecastAll(Pageable pageable) {
        return dailyForecastRepository.findAll(pageable)
                .map(stockDailyForecastMapper::toDTO);
    }

    private List<StockAlertDTO> computeAlerts() {
        return computeAlerts(null);
    }

    private List<StockAlertDTO> computeAlerts(Set<Integer> filterIds) {
        LocalDateTime since = LocalDateTime.now().minusWeeks(HISTORY_WEEKS);
        Map<Integer, BigDecimal> persistedPredictions = buildPredictionMap();

        if (filterIds != null && !filterIds.isEmpty()) {
            persistedPredictions.keySet().retainAll(filterIds);
        }

        if (persistedPredictions.isEmpty()) {
            return mergeExpirationAlerts(List.of(), filterIds);
        }

        Map<Integer, BigDecimal> pendingByProduct = buildPendingMap();
        Map<Integer, BigDecimal> stockByProduct = buildStockMap();
        Map<Integer, List<ProductBatch>> activeBatchesByProduct = buildActiveBatchesMap();

        List<StockAlertDTO> alerts = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : persistedPredictions.entrySet()) {
            Integer productId = entry.getKey();
            BigDecimal projected = entry.getValue();

            BigDecimal currentStock = stockByProduct.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal pending = pendingByProduct.getOrDefault(productId, BigDecimal.ZERO);

            StockAlertDTO alert = buildAlert(productId, currentStock, pending, projected, since, activeBatchesByProduct);
            if (alert != null) {
                alerts.add(alert);
            }
        }

            return mergeExpirationAlerts(alerts, filterIds);
    }

            private List<StockAlertDTO> mergeExpirationAlerts(List<StockAlertDTO> baseAlerts, Set<Integer> filterIds) {
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
                    .filter(java.util.Objects::nonNull)
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

                String expiringMessage = messageSource.getMessage(
                    MessageKey.STOCK_ALERT_MESSAGE_EXPIRING.getKey(),
                    new Object[] { nearestExpiration, expiringQuantity.setScale(3, RoundingMode.HALF_UP) },
                    LocaleContextHolder.getLocale());

                StockAlertDTO existing = alertsByProduct.get(productId);
                if (existing == null) {
                Product product = productRepository.findById(productId).orElse(null);
                if (product == null) {
                    continue;
                }

                StockAlertDTO expirationOnly = StockAlertDTO.builder()
                    .productId(productId)
                    .productName(product.getName())
                    .unit(product.getUnit())
                    .currentStock(product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO)
                    .pendingOrderQuantity(BigDecimal.ZERO)
                    .projectedConsumption(BigDecimal.ZERO)
                    .effectiveGap(BigDecimal.ZERO)
                    .estimatedDaysRemaining((int) daysToExpire)
                    .severity(expirationSeverity)
                    .resolution(AlertResolution.UNCOVERED)
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
                    .currentStock(existing.getCurrentStock())
                    .pendingOrderQuantity(existing.getPendingOrderQuantity())
                    .projectedConsumption(existing.getProjectedConsumption())
                    .effectiveGap(existing.getEffectiveGap())
                    .estimatedDaysRemaining(existing.getEstimatedDaysRemaining())
                    .severity(mergedSeverity)
                    .resolution(existing.getResolution())
                    .message(existing.getMessage() + " " + expiringMessage)
                    .nearestExpirationDate(nearestExpiration)
                    .expiringQuantity(expiringQuantity)
                    .topConsumingRecipes(existing.getTopConsumingRecipes())
                    .build();

                alertsByProduct.put(productId, merged);
            }

            return new ArrayList<>(alertsByProduct.values());
            }

    /**
     * Actualiza la predicción oficial de un producto basándose en el resultado del
     * predictor externo de IA (Python/Prophet).
     */
    @Transactional
    public void updatePredictionFromForecast(Integer productId, BigDecimal projectedConsumption) {
        updatePredictionFromForecast(productId, projectedConsumption, LocalDateTime.now());
    }

    /**
     * Actualiza la predicción oficial de un producto usando el timestamp recibido
     * por Kafka cuando esté disponible.
     */
    @Transactional
    public void updatePredictionFromForecast(Integer productId,
            BigDecimal projectedConsumption,
            LocalDateTime calculatedAt) {
        log.info("Actualizando predicción desde predictor externo para producto ID: {}. Nuevo valor: {}", 
            productId, projectedConsumption);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productId));

        StockPrediction prediction = predictionRepository.findById(productId)
            .orElseGet(() -> StockPrediction.builder()
                .product(product)
                .build());

        prediction.setProjectedConsumption(projectedConsumption);
        prediction.setUpdatedAt(calculatedAt != null ? calculatedAt : LocalDateTime.now());
        predictionRepository.save(prediction);
        
        log.debug("Predicción actualizada con éxito para producto {}", productId);
    }

    /**
     * Regla de negocio para clasificar una predicción recibida por Kafka:
     * - ALERT: el consumo proyectado a 14 días agota el stock actual en < 14 días
     * - PREDICTION: no agota el stock en ese horizonte
     */
    @Transactional(readOnly = true)
    public ForecastResultType classifyForecastResult(Integer productId, BigDecimal projectedConsumption) {
        if (productId == null || projectedConsumption == null || projectedConsumption.signum() < 0) {
            throw new IllegalArgumentException("Datos inválidos para clasificar forecast");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productId));

        BigDecimal currentStock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
        if (projectedConsumption.signum() == 0) {
            return ForecastResultType.PREDICTION;
        }

        // Consumo proyectado representa 14 días. Si projected > stock actual,
        // el stock se agotará en menos de 14 días.
        return projectedConsumption.compareTo(currentStock) > 0
                ? ForecastResultType.ALERT
                : ForecastResultType.PREDICTION;
    }

    /**
     * @deprecated Holt-Winters ya no participa en el flujo activo de predicción.
     *             Las predicciones oficiales se calculan exclusivamente mediante el
     *             predictor de IA y llegan por Kafka en {@code forecast-updates}.
     *             Se conserva solo por compatibilidad y referencia histórica.
     */
    @Deprecated
    public void updatePredictionsForRecipe(Integer recipeId) {
        log.warn(
            "[Deprecated] Recalculo Holt-Winters solicitado para receta ID: {}. " +
            "La ruta legacy está deshabilitada y las predicciones oficiales provienen exclusivamente de IA.",
            recipeId);
    }

    /**
     * @deprecated Implementación legacy conservada únicamente como referencia.
     *             No debe invocarse en producción porque sobrescribe predicciones
     *             generadas por IA.
     */
    @Deprecated
    @SuppressWarnings("unused")
    @Async
    @Transactional
    void runLegacyHoltWintersForecastForRecipe(Integer recipeId) {
        log.info("[Async] Iniciando recálculo de predicciones para receta ID: {}", recipeId);

        var recipeOpt = recipeRepository.findByIdWithDetails(recipeId);
        if (recipeOpt.isEmpty()) {
            log.warn("[Async] Receta no encontrada: {}", recipeId);
            return;
        }

        Recipe recipe = recipeOpt.get();
        Set<Integer> productIds = recipe.getComponents().stream()
                .map(c -> c.getProduct().getId())
                .collect(Collectors.toSet());

        if (productIds.isEmpty()) {
            return;
        }

        LocalDateTime since = LocalDateTime.now().minusWeeks(HISTORY_WEEKS);
        List<WeeklyIngredientConsumption> weeklyData = cookingAuditRepository.findWeeklyConsumptionPerIngredient(since,
                since);

        Map<Integer, List<Double>> consumptionByProduct = groupByProduct(weeklyData);
        LocalDateTime calculatedAt = LocalDateTime.now();

        for (Integer productId : productIds) {
            List<Double> consumption = consumptionByProduct.get(productId);
            if (consumption == null || consumption.isEmpty())
                continue;

            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                    messageSource.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND.getKey(), null,
                        LocaleContextHolder.getLocale()) + ": " + productId));

            List<BigDecimal> weeklySeries = consumption.stream()
                .map(value -> BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP))
                .collect(Collectors.toList());

            List<BigDecimal> dailySeries = forecaster.forecastDaily(consumption, SEASON_PERIOD, HORIZON_DAYS).stream()
                .map(value -> BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP))
                .collect(Collectors.toList());

            StockWeeklyConsumptionHistory weeklyHistory = weeklyHistoryRepository.findById(productId)
                .orElseGet(() -> StockWeeklyConsumptionHistory.builder()
                    .product(product)
                    .build());
            weeklyHistory.setWeeklyConsumption(weeklySeries);
            weeklyHistory.setWeeksOfHistory(HISTORY_WEEKS);
            weeklyHistory.setCalculatedAt(calculatedAt);
            weeklyHistoryRepository.save(weeklyHistory);

            StockDailyForecast dailyForecast = dailyForecastRepository.findById(productId)
                .orElseGet(() -> StockDailyForecast.builder()
                    .product(product)
                    .build());
            dailyForecast.setDailyForecast(dailySeries);
            dailyForecast.setHorizonDays(HORIZON_DAYS);
            dailyForecast.setCalculatedAt(calculatedAt);
            dailyForecastRepository.save(dailyForecast);

            double projectedRaw = forecaster.forecast(consumption, SEASON_PERIOD, HORIZON_DAYS);
            BigDecimal projected = BigDecimal.valueOf(projectedRaw).setScale(4, RoundingMode.HALF_UP);

            // Guardar o actualizar predicción
            StockPrediction prediction = predictionRepository.findById(productId)
                .orElseGet(() -> StockPrediction.builder()
                    .product(product)
                    .build());

            prediction.setProjectedConsumption(projected);
            predictionRepository.save(prediction);
            log.debug("[Async] Predicción actualizada para producto {}: {}", productId, projected);
        }

        log.info("[Async] Recálculo completado para receta ID: {}", recipeId);
    }

    private StockAlertDTO buildAlert(Integer productId,
            BigDecimal currentStock,
            BigDecimal pending,
            BigDecimal projected,
            LocalDateTime since,
            Map<Integer, List<ProductBatch>> activeBatchesByProduct) {

        // Recuperar nombre y unidad del producto
        var productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty())
            return null;
        var product = productOpt.get();

        BigDecimal effective = currentStock.add(pending);
        BigDecimal gap = projected.subtract(effective).setScale(3, RoundingMode.HALF_UP);

        // Días cubiertos por el stock efectivo
        int daysRemaining;
        if (projected.compareTo(BigDecimal.ZERO) <= 0) {
            daysRemaining = Integer.MAX_VALUE; // sin consumo proyectado → sin problema
        } else {
            BigDecimal dailyRate = projected.divide(BigDecimal.valueOf(HORIZON_DAYS), 6, RoundingMode.HALF_UP);
            if (dailyRate.compareTo(BigDecimal.ZERO) == 0) {
                daysRemaining = Integer.MAX_VALUE;
            } else {
                List<ProductBatch> batches = activeBatchesByProduct.getOrDefault(productId, List.of());
                daysRemaining = calculateDaysRemainingWithExpiration(dailyRate, batches, pending);
            }
        }

        AlertSeverity severity = classifySeverity(daysRemaining);
        if (severity == AlertSeverity.OK)
            return null; // sin alerta

        AlertResolution resolution = classifyResolution(gap, pending);
        String message = buildMessage(product.getName(), currentStock, pending, projected, gap,
                resolution, product.getUnit());

        List<String> topRecipes = cookingAuditRepository
                .findTopConsumingRecipesByProduct(productId, since);

        return StockAlertDTO.builder()
                .productId(productId)
                .productName(product.getName())
                .unit(product.getUnit())
                .currentStock(currentStock)
                .pendingOrderQuantity(pending)
                .projectedConsumption(projected)
                .effectiveGap(gap)
                .estimatedDaysRemaining(Math.min(daysRemaining, 999))
                .severity(severity)
                .resolution(resolution)
                .message(message)
                .nearestExpirationDate(null)
                .expiringQuantity(BigDecimal.ZERO)
                .topConsumingRecipes(topRecipes)
                .build();
    }

    // -------------------------------------------------------------------------
    // Clasificación
    // -------------------------------------------------------------------------

    private AlertSeverity classifySeverity(int days) {
        if (days >= 21)
            return AlertSeverity.OK;
        if (days >= 14)
            return AlertSeverity.LOW;
        if (days >= 7)
            return AlertSeverity.MEDIUM;
        if (days >= 3)
            return AlertSeverity.HIGH;
        return AlertSeverity.CRITICAL;
    }

    private AlertSeverity classifyExpirationSeverity(long daysToExpire) {
        if (daysToExpire < 3) {
            return AlertSeverity.CRITICAL;
        }
        if (daysToExpire < 7) {
            return AlertSeverity.HIGH;
        }
        if (daysToExpire < 14) {
            return AlertSeverity.MEDIUM;
        }
        return AlertSeverity.LOW;
    }

    private AlertResolution classifyResolution(BigDecimal gap, BigDecimal pending) {
        if (gap.compareTo(BigDecimal.ZERO) <= 0) {
            return AlertResolution.OK; // sin déficit — no debería llegar aquí, pero por seguridad
        }
        if (pending.compareTo(BigDecimal.ZERO) <= 0) {
            return AlertResolution.UNCOVERED;
        }
        // El gap ya descuenta el pending en la fórmula, así que si hay pending y gap >
        // 0
        // es una cobertura parcial
        return AlertResolution.PARTIALLY_COVERED;
    }

    // -------------------------------------------------------------------------
    // Generación de mensaje
    // -------------------------------------------------------------------------

    private String buildMessage(String name, BigDecimal stock, BigDecimal pending,
            BigDecimal projected, BigDecimal gap,
            AlertResolution resolution, String unit) {

        Locale locale = LocaleContextHolder.getLocale();

        String gapStr = gap.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String projStr = projected.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String stockStr = stock.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String pendStr = pending.setScale(2, RoundingMode.HALF_UP).toPlainString();

        Object[] args;
        String key;

        switch (resolution) {
            case COVERED_BY_ORDER -> {
                key = "stock.alert.message.covered";
                args = new Object[] { name, stockStr, unit, HORIZON_DAYS, projStr, unit, pendStr, unit };
            }
            case PARTIALLY_COVERED -> {
                key = "stock.alert.message.partially.covered";
                args = new Object[] { name, stockStr, unit, HORIZON_DAYS, projStr, unit, pendStr, unit, gapStr, unit };
            }
            case UNCOVERED -> {
                key = "stock.alert.message.uncovered";
                args = new Object[] { name, stockStr, unit, HORIZON_DAYS, projStr, unit, gapStr, unit };
            }
            default -> {
                key = "stock.alert.message.default";
                args = new Object[] { name, gapStr, unit };
            }
        }

        return messageSource.getMessage(key, args, locale);
    }

    // -------------------------------------------------------------------------
    // Helpers de agrupación
    // -------------------------------------------------------------------------

    /**
     * Agrupa las filas de consumo semanal por producto y devuelve, para cada uno,
     * la serie temporal de consumos semanales ordenada de más antigua a más
     * reciente.
     * Las semanas sin consumo se rellenan con 0.0 para mantener la continuidad.
     */
    private Map<Integer, List<Double>> groupByProduct(List<WeeklyIngredientConsumption> rows) {
        // Rango de índices de semana
        int minWeek = rows.stream().mapToInt(WeeklyIngredientConsumption::getWeekIndex).min().orElse(0);
        int maxWeek = rows.stream().mapToInt(WeeklyIngredientConsumption::getWeekIndex).max().orElse(0);

        // productId → (weekIndex → consumption)
        Map<Integer, Map<Integer, Double>> byProduct = new HashMap<>();
        for (WeeklyIngredientConsumption row : rows) {
            byProduct
                    .computeIfAbsent(row.getProductId(), k -> new HashMap<>())
                    .put(row.getWeekIndex(),
                            row.getTotalConsumed() != null ? row.getTotalConsumed().doubleValue() : 0.0);
        }

        // Expandir a lista continua rellenando semanas vacías con 0
        Map<Integer, List<Double>> result = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, Double>> entry : byProduct.entrySet()) {
            List<Double> series = new ArrayList<>();
            for (int w = minWeek; w <= maxWeek; w++) {
                series.add(entry.getValue().getOrDefault(w, 0.0));
            }
            result.put(entry.getKey(), series);
        }
        return result;
    }

    private Map<Integer, BigDecimal> buildPendingMap() {
        return orderDetailRepository.findPendingQuantityPerProduct()
                .stream()
                .collect(Collectors.toMap(
                        PendingProductQuantity::getProductId,
                        p -> p.getPendingQuantity() != null ? p.getPendingQuantity() : BigDecimal.ZERO));
    }

    private Map<Integer, BigDecimal> buildStockMap() {
        return productRepository.findAll()
                .stream()
                .filter(p -> !p.isHidden())
                .collect(Collectors.toMap(
                        p -> p.getId(),
                        p -> p.getCurrentStock() != null ? p.getCurrentStock() : BigDecimal.ZERO));
    }

    private Map<Integer, BigDecimal> buildPredictionMap() {
        return predictionRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        StockPrediction::getId,
                        StockPrediction::getProjectedConsumption));
    }

    private Map<Integer, List<ProductBatch>> buildActiveBatchesMap() {
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
}
