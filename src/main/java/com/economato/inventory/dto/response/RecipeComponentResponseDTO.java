package com.economato.inventory.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de respuesta de un componente de receta")
public class RecipeComponentResponseDTO {

    @Schema(description = "Identificador único del componente", example = "1")
    private Integer id;

    @Schema(description = "ID de la receta padre", example = "1")
    private Integer parentRecipeId;

    @Schema(description = "ID del producto asociado", example = "5")
    private Integer productId;

    @Schema(description = "Nombre del producto", example = "Arroz")
    private String productName;

    @Schema(description = "Cantidad del producto en la receta", example = "2.5")
    private BigDecimal quantity;

    @Schema(description = "Subtotal del producto en la receta (cantidad * precio unitario)", example = "10.50")
    private BigDecimal subtotal;
}
