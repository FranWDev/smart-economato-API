package com.economato.inventory.dto.projection;

import java.math.BigDecimal;

public interface RecipeComponentProjection {
    Integer getId();

    BigDecimal getQuantity();

    RecipeInfo getParentRecipe();

    ProductInfo getProduct();

    interface RecipeInfo {
        Integer getId();
    }

    interface ProductInfo {
        Integer getId();

        String getName();

        BigDecimal getUnitPrice();
    }
}
