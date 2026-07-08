package com.economato.inventory.application.dto.recipe.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeQuantityRequestDTO {

    @NotNull(message = "{error.recipe.id.required}")
    private Integer recipeId;

    @NotNull(message = "{error.recipe.quantity.required}")
    @DecimalMin(value = "0.01", message = "{error.recipe.quantity.min}")
    private BigDecimal quantity;
}
