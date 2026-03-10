package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.projection.PendingProductQuantity;
import com.economato.inventory.application.dto.projection.WeeklyIngredientConsumption;
import com.economato.inventory.application.dto.response.AlertResolution;
import com.economato.inventory.application.dto.response.AlertSeverity;
import com.economato.inventory.application.dto.response.DailyForecastResponseDTO;
import com.economato.inventory.application.dto.response.StockAlertDTO;
import com.economato.inventory.application.dto.response.StockPredictionResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyConsumptionResponseDTO;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.StockPrediction;
import com.economato.inventory.infrastructure.adapter.out.external.prediction.HoltWintersForecaster;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockPredictionRepository;
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
    private final HoltWintersForecaster forecaster;
    private final MessageSource messageSource;

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
        LocalDateTime since = LocalDateTime.now().minusWeeks(HISTORY_WEEKS);
        List<WeeklyIngredientConsumption> weeklyData = cookingAuditRepository.findWeeklyConsumptionPerIngredient(since,
                since);

        Map<Integer, List<Double>> consumptionByProduct = groupByProduct(weeklyData);

        return consumptionByProduct.entrySet().stream()
                .filter(entry -> productId == null || Objects.equals(entry.getKey(), productId))
                .map(entry -> productRepository.findById(entry.getKey())
                        .map(product -> WeeklyConsumptionResponseDTO.builder()
                                .productId(product.getId())
                                .productName(product.getName())
                                .unit(product.getUnit())
                                .weeklyConsumption(entry.getValue().stream()
                                        .map(value -> BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP))
                                        .collect(Collectors.toList()))
                                .weeksOfHistory(HISTORY_WEEKS)
                                .build())
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Devuelve el historial semanal de consumo para todos los productos con
     * historial en el período analizado.
     */
    @Transactional(readOnly = true)
    public List<WeeklyConsumptionResponseDTO> getWeeklyConsumptionHistoryAll() {
        return getWeeklyConsumptionHistory(null);
    }

    /**
     * Devuelve el historial semanal de consumo para todos los productos en formato
     * paginado.
     */
    @Transactional(readOnly = true)
    public Page<WeeklyConsumptionResponseDTO> getWeeklyConsumptionHistoryAll(Pageable pageable) {
        List<WeeklyConsumptionResponseDTO> all = getWeeklyConsumptionHistoryAll();
        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(start, end), pageable, all.size());
    }

        /**
         * Devuelve la proyección diaria de consumo para un producto concreto.
         */
        @Transactional(readOnly = true)
        public Optional<DailyForecastResponseDTO> getDailyForecast(Integer productId) {
        LocalDateTime since = LocalDateTime.now().minusWeeks(HISTORY_WEEKS);
        List<WeeklyIngredientConsumption> weeklyData = cookingAuditRepository.findWeeklyConsumptionPerIngredient(since,
            since);
        Map<Integer, List<Double>> consumptionByProduct = groupByProduct(weeklyData);

        List<Double> series = consumptionByProduct.get(productId);
        if (series == null || series.isEmpty()) {
            return Optional.empty();
        }

        return productRepository.findById(productId)
            .map(product -> {
                List<Double> daily = forecaster.forecastDaily(series, SEASON_PERIOD, HORIZON_DAYS);
                return DailyForecastResponseDTO.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .unit(product.getUnit())
                    .dailyForecast(daily.stream()
                        .map(v -> BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP))
                        .collect(Collectors.toList()))
                    .horizonDays(HORIZON_DAYS)
                    .calculatedAt(LocalDateTime.now())
                    .build();
            });
        }

            /**
             * Devuelve la proyección diaria de consumo para todos los productos con
             * historial en el período analizado.
             */
            @Transactional(readOnly = true)
            public List<DailyForecastResponseDTO> getDailyForecastAll() {
        LocalDateTime since = LocalDateTime.now().minusWeeks(HISTORY_WEEKS);
        List<WeeklyIngredientConsumption> weeklyData = cookingAuditRepository.findWeeklyConsumptionPerIngredient(since,
            since);
        Map<Integer, List<Double>> consumptionByProduct = groupByProduct(weeklyData);
        LocalDateTime calculatedAt = LocalDateTime.now();

        return consumptionByProduct.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
            .map(entry -> productRepository.findById(entry.getKey())
                .map(product -> {
                    List<Double> daily = forecaster.forecastDaily(entry.getValue(), SEASON_PERIOD, HORIZON_DAYS);
                    return DailyForecastResponseDTO.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .unit(product.getUnit())
                        .dailyForecast(daily.stream()
                            .map(v -> BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP))
                            .collect(Collectors.toList()))
                        .horizonDays(HORIZON_DAYS)
                        .calculatedAt(calculatedAt)
                        .build();
                })
                .orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        }

    /**
     * Devuelve la proyección diaria de consumo en formato paginado.
     */
    @Transactional(readOnly = true)
    public Page<DailyForecastResponseDTO> getDailyForecastAll(Pageable pageable) {
        List<DailyForecastResponseDTO> all = getDailyForecastAll();
        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(start, end), pageable, all.size());
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
            return List.of();
        }

        Map<Integer, BigDecimal> pendingByProduct = buildPendingMap();
        Map<Integer, BigDecimal> stockByProduct = buildStockMap();

        List<StockAlertDTO> alerts = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : persistedPredictions.entrySet()) {
            Integer productId = entry.getKey();
            BigDecimal projected = entry.getValue();

            BigDecimal currentStock = stockByProduct.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal pending = pendingByProduct.getOrDefault(productId, BigDecimal.ZERO);

            StockAlertDTO alert = buildAlert(productId, currentStock, pending, projected, since);
            if (alert != null) {
                alerts.add(alert);
            }
        }

        return alerts;
    }

    /**
     * Recalcula las predicciones para todos los ingredientes de una receta.
     * Se ejecuta de forma asíncrona (Virtual Threads habilitados).
     */
    @Async
    @Transactional
    public void updatePredictionsForRecipe(Integer recipeId) {
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

        for (Integer productId : productIds) {
            List<Double> consumption = consumptionByProduct.get(productId);
            if (consumption == null || consumption.isEmpty())
                continue;

            double projectedRaw = forecaster.forecast(consumption, SEASON_PERIOD, HORIZON_DAYS);
            BigDecimal projected = BigDecimal.valueOf(projectedRaw).setScale(4, RoundingMode.HALF_UP);

            // Guardar o actualizar predicción
            StockPrediction prediction = predictionRepository.findById(productId)
                    .orElseGet(() -> {
                        Product product = productRepository.findById(productId)
                                .orElseThrow(() -> new IllegalArgumentException(messageSource.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND.getKey(), null, LocaleContextHolder.getLocale()) + ": " + productId));
                        // Dejar que @MapsId sincronice el ID automáticamente desde product.id
                        return StockPrediction.builder()
                                .product(product)
                                .build();
                    });

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
            LocalDateTime since) {

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
                daysRemaining = effective.divide(dailyRate, 0, RoundingMode.FLOOR).intValue();
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
}
