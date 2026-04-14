package com.economato.inventory.application.usecase.smg.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RecipeSnapshot(
        Integer id,
        String name,
        BigDecimal cost,
        List<String> allergens,
        Map<Integer, BigDecimal> componentNeeds
) {
}
