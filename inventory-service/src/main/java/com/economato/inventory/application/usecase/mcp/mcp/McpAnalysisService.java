package com.economato.inventory.application.usecase.mcp.mcp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.mcp.mcp.McpCostBreakdownDto;
import com.economato.inventory.application.dto.mcp.mcp.McpMenuDayDto;
import com.economato.inventory.application.dto.recipe.mcp.McpMenuRecipeDto;
import com.economato.inventory.application.dto.mcp.mcp.McpMenuSuggestionDto;
import com.economato.inventory.application.dto.order.mcp.McpReorderSuggestionDto;
import com.economato.inventory.application.dto.stock.mcp.McpStockHealthDto;
import com.economato.inventory.application.dto.recipe.mcp.McpWasteRecipeSuggestionDto;
import com.economato.inventory.application.dto.mcp.mcp.McpWasteRiskDto;
import com.economato.inventory.application.dto.product.projection.PendingProductQuantity;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.stock.StockAlertService;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.stock.StockPrediction;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockPredictionRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiAnalysisProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpAnalysisService {

    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final StockPredictionRepository stockPredictionRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final ProductBatchService productBatchService;
    private final StockAlertService stockAlertService;
    private final AiAnalysisProperties aiAnalysisProperties;

    public List<McpReorderSuggestionDto> getReorderSuggestions() {
        Map<Integer, BigDecimal> pendingByProduct = orderDetailRepository.findPendingQuantityPerProduct().stream()
                .collect(Collectors.toMap(PendingProductQuantity::getProductId, PendingProductQuantity::getPendingQuantity));

        Map<Integer, StockPrediction> predictions = stockPredictionRepository.findAll().stream()
                .collect(Collectors.toMap(StockPrediction::getId, p -> p));

        List<McpReorderSuggestionDto> suggestions = new ArrayList<>();
        int horizon = aiAnalysisProperties.getReorderSuggestionHorizonDays();

        for (Product product : productRepository.findAllActive()) {
            StockPrediction prediction = predictions.get(product.getId());
            if (prediction == null || prediction.getProjectedConsumption() == null) {
                continue;
            }

            BigDecimal pending = pendingByProduct.getOrDefault(product.getId(), BigDecimal.ZERO);
            BigDecimal deficit = prediction.getProjectedConsumption()
                    .subtract(product.getCurrentStock())
                    .subtract(pending);
            if (deficit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal suggested = deficit.multiply(BigDecimal.valueOf(1.2)).setScale(3, RoundingMode.HALF_UP);
            String urgency = classifyUrgency(product.getCurrentStock(), prediction.getProjectedConsumption(), horizon);

            suggestions.add(new McpReorderSuggestionDto(
                    product.getId(),
                    product.getName(),
                    product.getCurrentStock(),
                    prediction.getProjectedConsumption(),
                    pending,
                    suggested,
                    product.getSupplier() != null ? product.getSupplier().getName() : null,
                    urgency
            ));
        }

        suggestions.sort(Comparator.comparing(McpReorderSuggestionDto::urgency).reversed());
        return suggestions;
    }

    public List<McpWasteRiskDto> getWasteRisk() {
        int daysThreshold = aiAnalysisProperties.getWasteRiskDaysThreshold();
        return productBatchService.getExpiringBatches(daysThreshold).stream()
                .filter(batch -> !batch.isDepleted())
                .filter(batch -> batch.getRemainingQuantity() != null && batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(this::mapWasteRisk)
                .sorted(Comparator.comparingInt(McpWasteRiskDto::daysUntilExpiry))
                .toList();
    }

    public McpMenuSuggestionDto getMenuOptimizer(BigDecimal budget, List<String> excludeAllergens) {
        BigDecimal safeBudget = budget == null || budget.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.valueOf(500) : budget;
        Set<String> excluded = excludeAllergens == null ? Set.of() : excludeAllergens.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        BigDecimal dailyBudget = safeBudget.divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP);
        var candidates = recipeRepository.findAll().stream()
                .filter(recipe -> recipe.getAllergens().stream().noneMatch(a -> excluded.contains(a.getName().toLowerCase())))
                .sorted(Comparator.comparing(r -> r.getTotalCost().divide(
                        r.getPortions() == null || r.getPortions().compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : r.getPortions(),
                        4,
                        RoundingMode.HALF_UP
            )))
            .limit(aiAnalysisProperties.getMenuOptimizerMaxRecipes())
                .toList();

        List<McpMenuDayDto> days = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int idx = 0;
        for (int day = 1; day <= 5; day++) {
            if (candidates.isEmpty()) {
                days.add(new McpMenuDayDto(day, List.of()));
                continue;
            }
            var recipe = candidates.get(idx % candidates.size());
            BigDecimal costPerPortion = recipe.getTotalCost().divide(
                    recipe.getPortions() == null || recipe.getPortions().compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : recipe.getPortions(),
                    4,
                    RoundingMode.HALF_UP
            );
            if (costPerPortion.compareTo(dailyBudget) <= 0) {
                days.add(new McpMenuDayDto(day, List.of(new McpMenuRecipeDto(
                        recipe.getId(),
                        recipe.getName(),
                        costPerPortion,
                        recipe.getAllergens().stream().map(a -> a.getName()).toList()
                ))));
                total = total.add(costPerPortion);
            } else {
                days.add(new McpMenuDayDto(day, List.of()));
            }
            idx++;
        }

        return new McpMenuSuggestionDto(days, total);
    }

    public McpCostBreakdownDto getCostBreakdown(LocalDate from, LocalDate to) {
        LocalDate safeFrom = from == null ? LocalDate.now().minusDays(7) : from;
        LocalDate safeTo = to == null ? LocalDate.now() : to;
        long days = ChronoUnit.DAYS.between(safeFrom, safeTo);
        if (days > aiAnalysisProperties.getCostBreakdownMaxDays()) {
            safeFrom = safeTo.minusDays(aiAnalysisProperties.getCostBreakdownMaxDays());
            days = aiAnalysisProperties.getCostBreakdownMaxDays();
        }

        List<Integer> productIds = productRepository.findAllActive().stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return new McpCostBreakdownDto(BigDecimal.ZERO, Map.of(), Map.of(), BigDecimal.ZERO);
        }

        var salidas = stockLedgerRepository.findSalidasByProductIdsAndDateRange(
                productIds,
                safeFrom.atStartOfDay(),
                safeTo.plusDays(1).atStartOfDay()
        );

        Map<String, BigDecimal> byProduct = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var ledger : salidas) {
            BigDecimal cost = ledger.getQuantityDelta().abs().multiply(ledger.getProduct().getUnitPrice());
            String productName = ledger.getProduct().getName();
            byProduct.merge(productName, cost, BigDecimal::add);
            total = total.add(cost);
        }

        BigDecimal dailyAverage = days <= 0 ? total : total.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
        return new McpCostBreakdownDto(total, Map.of(), byProduct, dailyAverage);
    }

    public McpStockHealthDto getStockHealthScore() {
        List<Product> products = productRepository.findAllActive();
        int totalProducts = products.size();

        Map<Integer, StockPrediction> predictions = stockPredictionRepository.findAll().stream()
                .collect(Collectors.toMap(StockPrediction::getId, p -> p));

        int productsAbovePrediction = (int) products.stream()
                .filter(product -> {
                    StockPrediction prediction = predictions.get(product.getId());
                    return prediction != null
                            && prediction.getProjectedConsumption() != null
                            && product.getCurrentStock().compareTo(prediction.getProjectedConsumption()) >= 0;
                })
                .count();

        List<ProductBatch> activeBatches = productBatchService.getAllActiveBatches();
        List<ProductBatch> expiringBatches = productBatchService.getExpiringBatches(aiAnalysisProperties.getWasteRiskDaysThreshold());
        Map<Integer, List<ProductBatch>> batchesByProductId = activeBatches.stream()
            .collect(Collectors.groupingBy(batch -> batch.getProduct().getId()));
        int totalActiveBatches = batchesByProductId.values().stream().mapToInt(List::size).sum();
        int batchesWithoutExpiryRisk = Math.max(0, totalActiveBatches - expiringBatches.size());

        int totalAlerts = stockAlertService.getActiveAlerts().size();
        int alertsResolved = Math.max(0, totalProducts - totalAlerts);

        double stockRatio = totalProducts == 0 ? 0d : (double) productsAbovePrediction / totalProducts;
        double batchRatio = totalActiveBatches == 0 ? 0d : (double) batchesWithoutExpiryRisk / totalActiveBatches;
        double alertRatio = totalProducts == 0 ? 0d : (double) alertsResolved / totalProducts;
        int score = (int) Math.round((aiAnalysisProperties.getStockHealthStockWeight() * stockRatio
            + aiAnalysisProperties.getStockHealthBatchWeight() * batchRatio
            + aiAnalysisProperties.getStockHealthAlertWeight() * alertRatio) * 100d);

        return new McpStockHealthDto(
                Math.max(0, Math.min(score, 100)),
                productsAbovePrediction,
                totalProducts,
                batchesWithoutExpiryRisk,
                totalActiveBatches,
                alertsResolved,
                totalAlerts
        );
    }

    private McpWasteRiskDto mapWasteRisk(ProductBatch batch) {
        var topRecipes = recipeCookingAuditRepository.findTopConsumingRecipesByProduct(
                batch.getProduct().getId(),
                LocalDateTime.now().minusDays(30)
        );

        List<McpWasteRecipeSuggestionDto> suggestions = topRecipes.stream()
                .map(name -> new McpWasteRecipeSuggestionDto(null, name, null, true))
                .toList();

        int daysUntilExpiry = Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), batch.getExpirationDate())));
        return new McpWasteRiskDto(
                batch.getId(),
                batch.getProduct().getId(),
                batch.getProduct().getName(),
                batch.getExpirationDate(),
                daysUntilExpiry,
                batch.getRemainingQuantity(),
                suggestions
        );
    }

    private String classifyUrgency(BigDecimal currentStock, BigDecimal projectedConsumption, int horizonDays) {
        if (projectedConsumption == null || projectedConsumption.compareTo(BigDecimal.ZERO) <= 0 || horizonDays <= 0) {
            return "LOW";
        }

        BigDecimal daily = projectedConsumption.divide(BigDecimal.valueOf(horizonDays), 6, RoundingMode.HALF_UP);
        if (daily.compareTo(BigDecimal.ZERO) <= 0) {
            return "LOW";
        }

        BigDecimal daysRemaining = currentStock.divide(daily, 2, RoundingMode.HALF_UP);
        if (daysRemaining.compareTo(BigDecimal.valueOf(3)) < 0) {
            return "CRITICAL";
        }
        if (daysRemaining.compareTo(BigDecimal.valueOf(7)) < 0) {
            return "HIGH";
        }
        if (daysRemaining.compareTo(BigDecimal.valueOf(14)) < 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
