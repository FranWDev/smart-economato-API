package com.economato.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import com.economato.inventory.model.MovementType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Movimiento individual de stock")
public class StockMovementItemDTO {

    @NotNull(message = "El ID del producto es obligatorio")
    @Schema(description = "ID del producto", example = "1")
    private Integer productId;

    @NotNull(message = "La cantidad es obligatoria")
    @Digits(integer = 10, fraction = 3, message = "La cantidad debe tener máximo 10 dígitos enteros y 3 decimales")
    @Schema(description = "Cantidad a sumar o restar (negativa para restar)", example = "10.500")
    private BigDecimal quantityDelta;

    @NotNull(message = "El tipo de movimiento es obligatorio")
    @Schema(description = "Tipo de movimiento", example = "AJUSTE")
    private MovementType movementType;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    @Schema(description = "Descripción del movimiento", example = "Rollback de receta errónea #123")
    private String description;
}
