package com.economato.inventory.application.usecase.mcp.mcp;

import com.economato.inventory.application.dto.stock.mcp.McpAlertDto;
import com.economato.inventory.application.dto.mcp.mcp.McpBatchDto;
import com.economato.inventory.application.dto.mcp.mcp.McpExpiringBatchDto;
import com.economato.inventory.application.dto.mcp.mcp.McpPredictionDto;
import com.economato.inventory.application.dto.product.mcp.McpProductDeepDto;
import com.economato.inventory.application.dto.product.mcp.McpProductDto;
import com.economato.inventory.application.dto.product.mcp.McpSupplierDeepDto;
import com.economato.inventory.application.dto.stock.response.StockAlertDTO;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.stock.StockAlertService;
import com.economato.inventory.domain.model.crisis.FoodCrisis;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.stock.StockPrediction;
import com.economato.inventory.domain.model.product.Supplier;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.crisis.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockDailyForecastRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockPredictionRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockWeeklyConsumptionHistoryRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiAnalysisProperties;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpProductReader {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final OrderRepository orderRepository;
    private final StockPredictionRepository stockPredictionRepository;
    private final StockDailyForecastRepository stockDailyForecastRepository;
    private final StockWeeklyConsumptionHistoryRepository stockWeeklyConsumptionHistoryRepository;
    private final FoodCrisisRepository foodCrisisRepository;
    private final ProductBatchService productBatchService;
    private final StockAlertService stockAlertService;
    private final AiAnalysisProperties aiAnalysisProperties;
    private final I18nService i18nService;

    public McpProductDeepDto getProductDeep(Integer productId) {
        Product product = productRepository.findByIdWithSupplier(productId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        List<ProductBatch> batches = productBatchService.getActiveBatches(productId);
        Integer daysToNearestExpiry = batches.isEmpty()
                ? null
                : Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), batches.get(0).getExpirationDate())));

        StockPrediction prediction = stockPredictionRepository.findById(productId).orElse(null);
        List<BigDecimal> dailyForecast = stockDailyForecastRepository.findOneById(productId)
                .map(f -> f.getDailyForecast())
                .orElseGet(List::of);
        List<BigDecimal> weeklyConsumption = stockWeeklyConsumptionHistoryRepository.findOneById(productId)
                .map(f -> f.getWeeklyConsumption())
                .orElseGet(List::of);

        return McpProductDeepDto.builder()
                .id(product.getId())
                .name(product.getName())
                .code(product.getProductCode())
                .stock(product.getCurrentStock())
                .unit(product.getUnit())
                .price(product.getUnitPrice())
                .supplierName(product.getSupplier() != null ? product.getSupplier().getName() : null)
                .alertLevel(resolveAlertLevel(product.getId()))
                .daysToNearestExpiry(daysToNearestExpiry)
                .prediction(prediction == null
                        ? null
                        : new McpPredictionDto(prediction.getProjectedConsumption(), prediction.getUpdatedAt()))
                .dailyForecast(dailyForecast)
                .weeklyConsumption(weeklyConsumption)
                .batches(mapBatches(batches))
                .lotQuantity(product.getLotQuantity())
                .build();
    }

    public List<McpBatchDto> getProductBatches(Integer productId) {
        return mapBatches(productBatchService.getActiveBatches(productId));
    }

    public List<BigDecimal> getProductForecast(Integer productId) {
        return stockDailyForecastRepository.findOneById(productId)
                .map(f -> f.getDailyForecast())
                .orElseGet(List::of);
    }

    public List<BigDecimal> getProductConsumptionHistory(Integer productId) {
        return stockWeeklyConsumptionHistoryRepository.findOneById(productId)
                .map(f -> f.getWeeklyConsumption())
                .orElseGet(List::of);
    }

    public McpSupplierDeepDto getSupplierDeep(Integer supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        List<McpProductDto> products = productRepository.findBySupplierId(supplierId).stream()
                .map(this::mapProduct)
                .toList();

        int recentOrderCount = Math.toIntExact(orderRepository.countBySupplierId(supplierId));

        boolean hasCrisis = foodCrisisRepository.existsByStatusAndSupplierId(
                FoodCrisis.CrisisStatus.ACTIVE,
                supplierId);

        return McpSupplierDeepDto.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .products(products)
                .recentOrderCount(recentOrderCount)
                .hasCrisis(hasCrisis)
                .build();
    }

    public List<McpExpiringBatchDto> getExpiringSoon(int days) {
        int safeDays = Math.max(1, days);
        return productBatchService.getExpiringBatches(safeDays).stream()
                .map(batch -> new McpExpiringBatchDto(
                        batch.getProduct().getId(),
                        batch.getProduct().getName(),
                        batch.getId(),
                        batch.getExpirationDate(),
                        batch.getRemainingQuantity(),
                        Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), batch.getExpirationDate())))
                ))
                .toList();
    }

    public List<McpAlertDto> getActiveAlerts() {
        return stockAlertService.getActiveAlerts().stream()
                .map(this::mapAlert)
                .toList();
    }

    public int getDefaultReorderHorizonDays() {
        return aiAnalysisProperties.getReorderSuggestionHorizonDays();
    }

    private McpAlertDto mapAlert(StockAlertDTO alert) {
        return new McpAlertDto(
                alert.getProductId(),
                alert.getProductName(),
                alert.getSeverity() != null ? alert.getSeverity().name() : null,
                alert.getResolution() != null ? alert.getResolution().name() : null,
                alert.getCurrentStock(),
                alert.getProjectedConsumption(),
                alert.getPendingOrderQuantity()
        );
    }

    private List<McpBatchDto> mapBatches(List<ProductBatch> batches) {
        return batches.stream()
                .map(batch -> new McpBatchDto(
                        batch.getId(),
                        batch.getExpirationDate(),
                        batch.getRemainingQuantity(),
                        batch.isDepleted(),
                        Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), batch.getExpirationDate())))
                ))
                .toList();
    }

    private McpProductDto mapProduct(Product product) {
        return McpProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .code(product.getProductCode())
                .stock(product.getCurrentStock())
                .unit(product.getUnit())
                .price(product.getUnitPrice())
                .lotQuantity(product.getLotQuantity())
                .build();
    }

    private String resolveAlertLevel(Integer productId) {
        return stockAlertService.getActiveAlerts().stream()
                .filter(alert -> productId.equals(alert.getProductId()))
                .findFirst()
                .map(alert -> alert.getSeverity().name())
                .orElse(null);
    }
}
