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

    @NotNull(message = "El ID del producto no puede ser nulo")
    @Schema(description = "ID del producto asociado al componente", example = "5")
    private Integer productId;

    @Schema(description = "ID de la receta asociada al componente (opcional al crear)", example = "1")
    private Integer recipeId;

    @NotNull(message = "La cantidad no puede ser nula")
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
    @Digits(integer = 10, fraction = 3, message = "La cantidad debe tener máximo 10 dígitos enteros y 3 decimales")
    @Schema(description = "Cantidad del producto en la receta", example = "2.5")
    private BigDecimal quantity;
}
