package com.economato.inventory.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear o actualizar un componente de receta")
public class RecipeComponentRequestDTO {

    @NotNull(message = "{validation.recipeComponentRequestDTO.productId.notNull}")
    @Schema(description = "ID del producto asociado al componente", example = "5")
    private Integer productId;

    @Schema(description = "ID de la receta asociada al componente (opcional al crear)", example = "1")
    private Integer recipeId;

    @NotNull(message = "{validation.recipeComponentRequestDTO.quantity.notNull}")
    @DecimalMin(value = "0.001", message = "{validation.recipeComponentRequestDTO.quantity.decimalMin}")
    @Digits(integer = 10, fraction = 3, message = "{validation.recipeComponentRequestDTO.quantity.digits}")
    @Schema(description = "Cantidad del producto en la receta", example = "2.5")
    private BigDecimal quantity;
}
