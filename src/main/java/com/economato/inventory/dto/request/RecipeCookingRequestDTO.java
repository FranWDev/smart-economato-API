package com.economato.inventory.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeCookingRequestDTO {

    @NotNull(message = "{validation.recipeCookingRequestDTO.recipeId.notNull}")
    @Positive(message = "{validation.recipeCookingRequestDTO.recipeId.positive}")
    private Integer recipeId;

    @NotNull(message = "{validation.recipeCookingRequestDTO.quantity.notNull}")
    @DecimalMin(value = "0.001", message = "{validation.recipeCookingRequestDTO.quantity.decimalMin}")
    @Digits(integer = 10, fraction = 3, message = "{validation.recipeCookingRequestDTO.quantity.digits}")
    private BigDecimal quantity;

    @Size(max = 500, message = "{validation.recipeCookingRequestDTO.details.size}")
    private String details;
}
