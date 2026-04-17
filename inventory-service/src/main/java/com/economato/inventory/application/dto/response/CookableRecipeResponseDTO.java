package com.economato.inventory.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de receta con su cantidad cocinable según stock actual")
public class CookableRecipeResponseDTO {

    @Schema(description = "Identificador único de la receta", example = "1")
    private Integer id;

    @Schema(description = "Nombre de la receta", example = "Paella Valenciana")
    private String name;

    @Schema(description = "Número de raciones que rinde la receta", example = "10.0")
    private BigDecimal portions;

    @Schema(description = "Cantidad máxima cocinable con el stock actual", example = "8")
    private BigDecimal cookableQuantity;

    @Schema(description = "Indica si se puede cocinar al menos una unidad de receta", example = "true")
    private boolean cookable;

    @Schema(description = "Componentes con cantidades requeridas y stock disponible")
    private List<CookableRecipeComponentResponseDTO> components;

    @Schema(description = "Lista de alérgenos asociados a la receta")
    private List<AllergenResponseDTO> allergens;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Componente de receta con stock disponible")
    public static class CookableRecipeComponentResponseDTO {
        @Schema(description = "ID del producto asociado", example = "5")
        private Integer productId;

        @Schema(description = "Nombre del producto", example = "Arroz")
        private String productName;

        @Schema(description = "Unidad de medida", example = "kg")
        private String unit;

        @Schema(description = "Cantidad requerida por unidad de receta", example = "2.5")
        private BigDecimal requiredQuantity;

        @Schema(description = "Stock actual del producto", example = "12.0")
        private BigDecimal availableStock;

        @Schema(description = "Stock reservado por otros planes activos", example = "3.5")
        private BigDecimal reservedByOtherPlans;
    }
}