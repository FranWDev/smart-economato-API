package com.economato.inventory.application.usecase.stock;

import com.economato.inventory.application.dto.stock.event.ForecastResultType;
import com.economato.inventory.application.dto.stock.response.AlertSeverity;
import com.economato.inventory.application.dto.stock.response.DailyForecastResponseDTO;
import com.economato.inventory.application.dto.product.response.ProductBatchResponseDTO;
import com.economato.inventory.application.dto.stock.response.StockAlertDTO;
import com.economato.inventory.application.dto.stock.response.StockPredictionResponseDTO;
import com.economato.inventory.application.dto.shared.response.WeeklyConsumptionResponseDTO;
import com.economato.inventory.application.mapper.stock.StockDailyForecastMapper;
import com.economato.inventory.application.mapper.stock.StockWeeklyConsumptionHistoryMapper;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.stock.StockDailyForecast;
import com.economato.inventory.domain.model.stock.StockPrediction;
import com.economato.inventory.domain.model.stock.StockWeeklyConsumptionHistory;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockDailyForecastRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockPredictionRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockWeeklyConsumptionHistoryRepository;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Facade y despachador de alertas de stock.
 * Delega cálculos específicos a StockThresholdEvaluator y BatchExpirationMonitor.
 */
@Slf4j
@Service
public class StockAlertService {

    private final ProductRepository productRepository;
    private final StockPredictionRepository predictionRepository;
    private final StockDailyForecastRepository dailyForecastRepository;
    private final StockWeeklyConsumptionHistoryRepository weeklyHistoryRepository;
    private final StockDailyForecastMapper stockDailyForecastMapper;
    private final StockWeeklyConsumptionHistoryMapper stockWeeklyConsumptionHistoryMapper;
    private final ProductBatchService productBatchService;
    private final I18nService i18nService;

    private final StockThresholdEvaluator stockThresholdEvaluator;
    private final BatchExpirationMonitor batchExpirationMonitor;

    @Autowired
    public StockAlertService(
            ProductRepository productRepository,
            StockPredictionRepository predictionRepository,
            StockDailyForecastRepository dailyForecastRepository,
            StockWeeklyConsumptionHistoryRepository weeklyHistoryRepository,
            StockDailyForecastMapper stockDailyForecastMapper,
            StockWeeklyConsumptionHistoryMapper stockWeeklyConsumptionHistoryMapper,
            ProductBatchService productBatchService,
            I18nService i18nService,
            StockThresholdEvaluator stockThresholdEvaluator,
            BatchExpirationMonitor batchExpirationMonitor) {
        this.productRepository = productRepository;
        this.predictionRepository = predictionRepository;
        this.dailyForecastRepository = dailyForecastRepository;
        this.weeklyHistoryRepository = weeklyHistoryRepository;
        this.stockDailyForecastMapper = stockDailyForecastMapper;
        this.stockWeeklyConsumptionHistoryMapper = stockWeeklyConsumptionHistoryMapper;
        this.productBatchService = productBatchService;
        this.i18nService = i18nService;
        this.stockThresholdEvaluator = stockThresholdEvaluator;
        this.batchExpirationMonitor = batchExpirationMonitor;
    }

    // Overloaded secondary constructor for backwards compatibility with tests (12 args).
    public StockAlertService(
            com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository cookingAuditRepository,
            com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderDetailRepository orderDetailRepository,
            ProductRepository productRepository,
            com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository recipeRepository,
            StockPredictionRepository predictionRepository,
            StockDailyForecastRepository dailyForecastRepository,
            StockWeeklyConsumptionHistoryRepository weeklyHistoryRepository,
            StockDailyForecastMapper stockDailyForecastMapper,
            StockWeeklyConsumptionHistoryMapper stockWeeklyConsumptionHistoryMapper,
            com.economato.inventory.infrastructure.adapter.out.external.stock.prediction.HoltWintersForecaster forecaster,
            I18nService i18nService,
            ProductBatchService productBatchService) {
        this(
                productRepository,
                predictionRepository,
                dailyForecastRepository,
                weeklyHistoryRepository,
                stockDailyForecastMapper,
                stockWeeklyConsumptionHistoryMapper,
                productBatchService,
                i18nService,
                new StockThresholdEvaluator(
                        cookingAuditRepository,
                        orderDetailRepository,
                        productRepository,
                        recipeRepository,
                        predictionRepository,
                        i18nService,
                        productBatchService,
                        null,
                        new BatchExpirationMonitor(productBatchService, i18nService, null)
                ),
                new BatchExpirationMonitor(productBatchService, i18nService, null)
        );
    }

    @Cacheable(value = "stock_alerts", key = "'active'")
    @Transactional(readOnly = true)
    public List<StockAlertDTO> getActiveAlerts() {
        return stockThresholdEvaluator.computeAlerts().stream()
                .filter(a -> a.getSeverity() != AlertSeverity.OK)
                .sorted(Comparator.comparing(StockAlertDTO::getSeverity).reversed())
                .collect(Collectors.toList());
    }

    @Cacheable(value = "stock_alerts", key = "'severity:' + #minSeverity.name()")
    @Transactional(readOnly = true)
    public List<StockAlertDTO> getAlertsBySeverity(AlertSeverity minSeverity) {
        return getActiveAlerts().stream()
                .filter(a -> a.getSeverity().ordinal() >= minSeverity.ordinal())
                .collect(Collectors.toList());
    }

    @Cacheable(value = "stock_alerts", key = "'product:' + #productId", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<StockAlertDTO> getAlertByProductId(Integer productId) {
        return stockThresholdEvaluator.computeAlerts(Set.of(productId)).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<StockAlertDTO> getAlertsByProductIds(Collection<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return stockThresholdEvaluator.computeAlerts(new HashSet<>(productIds));
    }

    @Cacheable(value = "stock_predictions", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<StockPredictionResponseDTO> getAllPredictions(Pageable pageable) {
        Set<Integer> expiringProductIds = productBatchService.getExpiringBatches(7).stream()
                .map(batch -> batch.getProduct().getId())
                .collect(Collectors.toSet());

        List<StockPredictionResponseDTO> allValidPredictions = predictionRepository.findAllActive().stream()
                .filter(p -> !expiringProductIds.contains(p.getId()))
                .map(prediction -> StockPredictionResponseDTO.builder()
                        .productId(prediction.getId())
                        .productName(prediction.getProduct().getName())
                        .projectedConsumption(prediction.getProjectedConsumption())
                        .projectedConsumptionUnit(prediction.getProduct().getUnit())
                        .currentStock(prediction.getProduct().getCurrentStock())
                        .lotQuantity(prediction.getProduct().getLotQuantity())
                        .alertType(com.economato.inventory.application.dto.stock.response.AlertType.PREDICTION)
                        .updatedAt(prediction.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        if (pageable.getSort().isSorted()) {
            allValidPredictions.sort((p1, p2) -> {
                for (Sort.Order order : pageable.getSort()) {
                    int result = compareByProperty(p1, p2, order.getProperty());
                    if (result != 0) {
                        return order.isAscending() ? result : -result;
                    }
                }
                return 0;
            });
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allValidPredictions.size());

        List<StockPredictionResponseDTO> pageContent = new ArrayList<>();
        if (start < allValidPredictions.size()) {
            pageContent = allValidPredictions.subList(start, end);
        }

        return new PageImpl<>(pageContent, pageable, allValidPredictions.size());
    }

    @Cacheable(value = "weekly_consumption", key = "#productId != null ? #productId : 'all'")
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

    @Transactional(readOnly = true)
    public List<WeeklyConsumptionResponseDTO> getWeeklyConsumptionHistoryAll() {
        return weeklyHistoryRepository.findAll().stream()
                .sorted(Comparator.comparing(StockWeeklyConsumptionHistory::getId))
                .map(stockWeeklyConsumptionHistoryMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<WeeklyConsumptionResponseDTO> getWeeklyConsumptionHistoryAll(Pageable pageable) {
        return weeklyHistoryRepository.findAll(pageable)
                .map(stockWeeklyConsumptionHistoryMapper::toDTO);
    }

    @Cacheable(value = "daily_forecast", key = "#productId")
    @Transactional(readOnly = true)
    public Optional<DailyForecastResponseDTO> getDailyForecast(Integer productId) {
        return dailyForecastRepository.findOneById(productId)
                .map(forecast -> {
                    DailyForecastResponseDTO dto = stockDailyForecastMapper.toDTO(forecast);
                    List<ProductBatch> activeBatches = productBatchService.getActiveBatches(productId);
                    dto.setActiveBatches(activeBatches.stream()
                            .map(batch -> new ProductBatchResponseDTO(
                                    batch.getId(),
                                    batch.getProduct().getId(),
                                    batch.getProduct().getName(),
                                    batch.getExpirationDate(),
                                    batch.getInitialQuantity(),
                                    batch.getRemainingQuantity(),
                                    batch.getReceivedAt(),
                                    batch.getBatchCode(),
                                    batch.isDepleted(),
                                    batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(LocalDate.now()),
                                    batch.getExpirationDate() != null 
                                            ? (int) Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), batch.getExpirationDate()))
                                            : 0
                            ))
                            .collect(Collectors.toList()));
                    return dto;
                });
    }

    @Cacheable(value = "daily_forecast", key = "'all'")
    @Transactional(readOnly = true)
    public List<DailyForecastResponseDTO> getDailyForecastAll() {
        return dailyForecastRepository.findAll().stream()
                .sorted(Comparator.comparing(StockDailyForecast::getId))
                .map(stockDailyForecastMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "daily_forecast", key = "'page:' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<DailyForecastResponseDTO> getDailyForecastAll(Pageable pageable) {
        return dailyForecastRepository.findAll(pageable)
                .map(stockDailyForecastMapper::toDTO);
    }

    @CacheEvict(value = { "stock_alerts", "stock_predictions", "daily_forecast", "weekly_consumption" }, allEntries = true)
    @RealtimeSync(entityType = "stock_alerts", action = "UPDATE", idFromArg = 0, affectedDomains = {"stock_alerts"})
    @Transactional
    public void updatePredictionFromForecast(Integer productId, BigDecimal projectedConsumption) {
        stockThresholdEvaluator.updatePredictionFromForecast(productId, projectedConsumption, LocalDateTime.now());
    }

    @CacheEvict(value = { "stock_alerts", "stock_predictions", "daily_forecast", "weekly_consumption" }, allEntries = true)
    @Transactional
    public void updatePredictionFromForecast(Integer productId, BigDecimal projectedConsumption, LocalDateTime calculatedAt) {
        stockThresholdEvaluator.updatePredictionFromForecast(productId, projectedConsumption, calculatedAt);
    }

    @Transactional(readOnly = true)
    public ForecastResultType classifyForecastResult(Integer productId, BigDecimal projectedConsumption) {
        return stockThresholdEvaluator.classifyForecastResult(productId, projectedConsumption);
    }

    @Deprecated
    public void updatePredictionsForRecipe(Integer recipeId) {
        log.warn("[Deprecated] Recalculo Holt-Winters solicitado para receta ID: {}. La ruta legacy está deshabilitada.", recipeId);
    }

    private int compareByProperty(StockPredictionResponseDTO p1, StockPredictionResponseDTO p2, String property) {
        switch (property) {
            case "productName":
                return p1.getProductName().compareToIgnoreCase(p2.getProductName());
            case "projectedConsumption":
                return p1.getProjectedConsumption().compareTo(p2.getProjectedConsumption());
            case "currentStock":
                BigDecimal s1 = p1.getCurrentStock() != null ? p1.getCurrentStock() : BigDecimal.ZERO;
                BigDecimal s2 = p2.getCurrentStock() != null ? p2.getCurrentStock() : BigDecimal.ZERO;
                return s1.compareTo(s2);
            case "updatedAt":
                if (p1.getUpdatedAt() == null && p2.getUpdatedAt() == null) return 0;
                if (p1.getUpdatedAt() == null) return -1;
                if (p2.getUpdatedAt() == null) return 1;
                return p1.getUpdatedAt().compareTo(p2.getUpdatedAt());
            default:
                return 0;
        }
    }
}
