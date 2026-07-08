package com.economato.inventory.application.usecase.mcp.mcp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.stock.mcp.McpAlertDto;
import com.economato.inventory.application.dto.mcp.mcp.McpBatchDto;
import com.economato.inventory.application.dto.mcp.mcp.McpComponentDto;
import com.economato.inventory.application.dto.mcp.mcp.McpComponentFeasibilityDto;
import com.economato.inventory.application.dto.crisis.mcp.McpCrisisDto;
import com.economato.inventory.application.dto.crisis.mcp.McpCrisisProductDto;
import com.economato.inventory.application.dto.mcp.mcp.McpExpiringBatchDto;
import com.economato.inventory.application.dto.mcp.mcp.McpFeasibilityDto;
import com.economato.inventory.application.dto.ledger.mcp.McpLedgerEntryDto;
import com.economato.inventory.application.dto.mcp.mcp.McpPredictionDto;
import com.economato.inventory.application.dto.product.mcp.McpProductDeepDto;
import com.economato.inventory.application.dto.product.mcp.McpProductDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDeepDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDto;
import com.economato.inventory.application.dto.weeklyplan.mcp.McpSlotDto;
import com.economato.inventory.application.dto.product.mcp.McpSupplierDeepDto;
import com.economato.inventory.application.dto.weeklyplan.mcp.McpWeeklyPlanDeepDto;
import com.economato.inventory.application.dto.stock.response.StockAlertDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.stock.StockAlertService;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanStockReservationService;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanService;
import com.economato.inventory.domain.model.crisis.FoodCrisis;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.stock.StockPrediction;
import com.economato.inventory.domain.model.product.Supplier;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.crisis.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockDailyForecastRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockPredictionRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockWeeklyConsumptionHistoryRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiAnalysisProperties;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpToolReadService {

    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final SupplierRepository supplierRepository;
    private final OrderRepository orderRepository;
    private final StockPredictionRepository stockPredictionRepository;
    private final StockDailyForecastRepository stockDailyForecastRepository;
    private final StockWeeklyConsumptionHistoryRepository stockWeeklyConsumptionHistoryRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final FoodCrisisRepository foodCrisisRepository;
    private final ProductBatchService productBatchService;
    private final StockAlertService stockAlertService;
        private final WeeklyPlanStockReservationService weeklyPlanStockReservationService;
    private final WeeklyPlanService weeklyPlanService;
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

    public McpRecipeDeepDto getRecipeDeep(Integer recipeId) {
        Recipe recipe = recipeRepository.findByIdWithDetails(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        List<McpComponentDto> components = recipe.getComponents().stream()
                .map(component -> {
                    Product product = component.getProduct();
                    BigDecimal stock = product != null ? product.getCurrentStock() : BigDecimal.ZERO;
                    BigDecimal availability = product != null && product.getAvailabilityPercentage() != null
                            ? product.getAvailabilityPercentage()
                            : BigDecimal.valueOf(100);
                    BigDecimal usableStock = stock.multiply(availability).divide(BigDecimal.valueOf(100), 4, RoundingMode.DOWN);
                    boolean sufficient = usableStock.compareTo(component.getQuantity()) >= 0;
                    return new McpComponentDto(
                            product != null ? product.getId() : null,
                            product != null ? product.getName() : null,
                            component.getQuantity(),
                            product != null ? product.getUnit() : null,
                            usableStock,
                            sufficient
                    );
                })
                .toList();

        BigDecimal portions = recipe.getPortions() == null || recipe.getPortions().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE
                : recipe.getPortions();
        BigDecimal costPerPortion = recipe.getTotalCost().divide(portions, 4, RoundingMode.HALF_UP);
        long recentCookingCount = recipeCookingAuditRepository.countByRecipeIdAndCookingDateAfter(
                recipeId,
                LocalDateTime.now().minusDays(30)
        );

        return McpRecipeDeepDto.builder()
                .id(recipe.getId())
                .name(recipe.getName())
                .code(recipe.getId().toString())
                .cost(recipe.getTotalCost())
                .description(recipe.getPresentation())
                .preparation(recipe.getElaboration())
                .components(components)
                .allergens(recipe.getAllergens().stream().map(a -> a.getName()).toList())
                .costPerPortion(costPerPortion)
                .recentCookingCount((int) recentCookingCount)
                .build();
    }

    public McpFeasibilityDto checkFeasibility(Integer recipeId, BigDecimal portions) {
        Recipe recipe = recipeRepository.findByIdWithDetails(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        BigDecimal safePortions = portions == null || portions.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : portions;
        BigDecimal recipePortions = recipe.getPortions() == null || recipe.getPortions().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE
                : recipe.getPortions();
        Map<Integer, BigDecimal> reservedStock = weeklyPlanStockReservationService.calculateReservedStock(null);

        List<McpComponentFeasibilityDto> components = new ArrayList<>();
        boolean feasible = true;
        for (var component : recipe.getComponents()) {
            Product product = component.getProduct();
            BigDecimal required = component.getQuantity().multiply(safePortions).divide(recipePortions, 4, RoundingMode.HALF_UP);
            BigDecimal availability = product.getAvailabilityPercentage() == null ? BigDecimal.valueOf(100) : product.getAvailabilityPercentage();
            BigDecimal available = product.getCurrentStock().multiply(availability).divide(BigDecimal.valueOf(100), 4, RoundingMode.DOWN)
                    .subtract(reservedStock.getOrDefault(product.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            BigDecimal deficit = required.subtract(available);
            if (deficit.compareTo(BigDecimal.ZERO) > 0) {
                feasible = false;
            } else {
                deficit = null;
            }
            components.add(new McpComponentFeasibilityDto(product.getId(), product.getName(), required, available, deficit));
        }

        return new McpFeasibilityDto(feasible, components);
    }

    public McpWeeklyPlanDeepDto getCurrentWeeklyPlanDeep() {
        WeeklyPlanResponseDTO plan = weeklyPlanService.getCurrentWeekPlan();
        List<McpSlotDto> slots = plan.getSlots().stream()
                .map(this::mapSlot)
                .toList();
        return new McpWeeklyPlanDeepDto(
                plan.getId(),
                plan.getStatus() != null ? plan.getStatus().name() : null,
                plan.getWeekStartDate(),
                plan.getWeekEndDate(),
                slots,
                Collections.emptyMap()
        );
    }

    public List<McpBatchDto> getProductBatches(Integer productId) {
        return mapBatches(productBatchService.getActiveBatches(productId));
    }

    public List<McpLedgerEntryDto> getProductLedger(Integer productId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return stockLedgerRepository.findByProductId(
                        productId,
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "transactionTimestamp"))
                )
                .getContent()
                .stream()
                .map(this::mapLedger)
                .toList();
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

    public List<McpCrisisDto> getActiveCrises() {
        return foodCrisisRepository.findByStatusWithDetails(FoodCrisis.CrisisStatus.ACTIVE).stream()
                .map(crisis -> new McpCrisisDto(
                        crisis.getId(),
                        crisis.getCrisisCode(),
                        crisis.getReason(),
                        crisis.getSupplier() != null ? crisis.getSupplier().getName() : null,
                        crisis.getStatus() != null ? crisis.getStatus().name() : null,
                        crisis.getDateFrom(),
                        crisis.getDateTo(),
                        crisis.getAffectedProducts() == null ? List.of() : crisis.getAffectedProducts().stream()
                                .map(ap -> new McpCrisisProductDto(
                                        ap.getProduct() != null ? ap.getProduct().getId() : null,
                                        ap.getProduct() != null ? ap.getProduct().getName() : null,
                                        ap.getOriginalAvailabilityPercentage()
                                ))
                                .toList()
                ))
                .toList();
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

    public List<McpRecipeDto> getRecipesByAllergenExclusion(List<String> excludeAllergens) {
        List<String> excluded = excludeAllergens == null ? List.of() : excludeAllergens.stream()
                .map(String::toLowerCase)
                .toList();

        return recipeRepository.findAllWithAllergens().stream()
                .filter(recipe -> recipe.getAllergens().stream().noneMatch(a -> excluded.contains(a.getName().toLowerCase())))
                .map(recipe -> McpRecipeDto.builder()
                        .id(recipe.getId())
                        .name(recipe.getName())
                        .code(recipe.getId().toString())
                        .cost(recipe.getTotalCost())
                        .allergenCount(recipe.getAllergens().size())
                        .description(recipe.getPresentation())
                        .preparation(recipe.getElaboration())
                        .build())
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

    private McpLedgerEntryDto mapLedger(StockLedger tx) {
        return new McpLedgerEntryDto(
                tx.getId(),
                tx.getMovementType().name(),
                tx.getQuantityDelta(),
                tx.getResultingStock(),
                tx.getDescription(),
                tx.getTransactionTimestamp(),
                tx.getUser() != null ? tx.getUser().getName() : null
        );
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

    private McpSlotDto mapSlot(WeeklyPlanSlotResponseDTO slot) {
        return new McpSlotDto(
                slot.getId(),
                slot.getRecipeId(),
                slot.getRecipeName(),
                slot.getQuantity(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getStatus() != null ? slot.getStatus().name() : null
        );
    }
}
