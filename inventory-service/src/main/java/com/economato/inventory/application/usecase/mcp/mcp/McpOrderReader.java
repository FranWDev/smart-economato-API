package com.economato.inventory.application.usecase.mcp.mcp;

import com.economato.inventory.application.dto.mcp.mcp.McpComponentDto;
import com.economato.inventory.application.dto.mcp.mcp.McpComponentFeasibilityDto;
import com.economato.inventory.application.dto.mcp.mcp.McpFeasibilityDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDeepDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDto;
import com.economato.inventory.application.dto.weeklyplan.mcp.McpSlotDto;
import com.economato.inventory.application.dto.weeklyplan.mcp.McpWeeklyPlanDeepDto;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanService;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanStockReservationService;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpOrderReader {

    private final RecipeRepository recipeRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final WeeklyPlanStockReservationService weeklyPlanStockReservationService;
    private final WeeklyPlanService weeklyPlanService;
    private final I18nService i18nService;

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
