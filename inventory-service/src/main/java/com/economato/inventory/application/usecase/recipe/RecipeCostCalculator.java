package com.economato.inventory.application.usecase.recipe;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.recipe.response.RecipeAverageCostResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeCountResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeStatsResponseDTO;
import com.economato.inventory.application.mapper.shared.StatsMapper;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;

@Service
@Transactional(readOnly = true)
public class RecipeCostCalculator {

    private final RecipeRepository repository;
    private final StatsMapper statsMapper;

    public RecipeCostCalculator(RecipeRepository repository, StatsMapper statsMapper) {
        this.repository = repository;
        this.statsMapper = statsMapper;
    }

    public RecipeStatsResponseDTO getRecipeStats() {
        long total = repository.countByIsHiddenFalse();
        long withAllergens = repository.countWithAllergens();
        long withoutAllergens = repository.countWithoutAllergens();
        BigDecimal averagePrice = repository.getAveragePrice();

        return statsMapper.toRecipeStatsDTO(total, withAllergens, withoutAllergens, averagePrice);
    }

    public RecipeCountResponseDTO getRecipesWithAllergensCount() {
        return statsMapper.toRecipeCountDTO(repository.countWithAllergens());
    }

    public RecipeCountResponseDTO getRecipesWithoutAllergensCount() {
        return statsMapper.toRecipeCountDTO(repository.countWithoutAllergens());
    }

    public RecipeAverageCostResponseDTO getRecipesAverageCost() {
        return statsMapper.toRecipeAverageCostDTO(repository.getAveragePrice());
    }

    public void calculateTotalCost(Recipe recipe) {
        if (recipe.getComponents() == null) {
            recipe.setTotalCost(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return;
        }

        BigDecimal totalCost = recipe.getComponents().stream()
                .filter(component -> component.getQuantity() != null &&
                        component.getProduct() != null &&
                        component.getProduct().getUnitPrice() != null)
                .map(component -> {
                    BigDecimal qty = component.getQuantity();
                    BigDecimal price = component.getProduct().getUnitPrice();
                    BigDecimal pct = component.getProduct().getAvailabilityPercentage() != null
                            ? component.getProduct().getAvailabilityPercentage()
                            : new BigDecimal("100.00");

                    if (pct.compareTo(BigDecimal.ZERO) <= 0) {
                        return qty.multiply(price);
                    }

                    // Cost = (NetQty * 100 / AvailabilityPct) * Price
                    return qty.multiply(new BigDecimal("100"))
                            .divide(pct, 10, RoundingMode.HALF_UP)
                            .multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        recipe.setTotalCost(totalCost);
    }
}
