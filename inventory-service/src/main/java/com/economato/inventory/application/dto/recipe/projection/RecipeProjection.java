package com.economato.inventory.application.dto.recipe.projection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface RecipeProjection {
    Integer getId();

    String getName();

    String getElaboration();

    String getPresentation();

    BigDecimal getTotalCost();
    BigDecimal getSellingPrice();
    BigDecimal getPortions();


    boolean getIsHidden();

    List<RecipeComponentSummary> getComponents();

    Set<AllergenInfo> getAllergens();

    interface RecipeComponentSummary {
        Integer getId();

        BigDecimal getQuantity();

        ProductInfo getProduct();

        interface ProductInfo {
            Integer getId();

            String getName();

            BigDecimal getUnitPrice();
            
            BigDecimal getAvailabilityPercentage();

            String getUnit();

        }
    }

    interface AllergenInfo {
        Integer getId();

        String getName();
    }
}
