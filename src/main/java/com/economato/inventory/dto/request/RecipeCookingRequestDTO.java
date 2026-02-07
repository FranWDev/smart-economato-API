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

    @NotNull(message = "El ID de la receta es obligatorio")
    @Positive(message = "El ID de la receta debe ser positivo")
    private Integer recipeId;

    @NotNull(message = "La cantidad a cocinar es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0")
    @Digits(integer = 10, fraction = 3, message = "La cantidad debe tener máximo 10 enteros y 3 decimales")
    private BigDecimal quantity;

    @Size(max = 500, message = "Los detalles no pueden exceder 500 caracteres")
    private String details;
}
