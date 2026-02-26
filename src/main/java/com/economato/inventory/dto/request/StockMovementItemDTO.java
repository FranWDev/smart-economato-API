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

    @NotNull(message = "{stockmovementitemdto.notnull.el.id.del.producto.es.obligato}")
    @Schema(description = "ID del producto", example = "1")
    private Integer productId;

    @NotNull(message = "{stockmovementitemdto.notnull.la.cantidad.es.obligatoria}")
    @Digits(integer = 10, fraction = 3, message = "{stockmovementitemdto.digits.la.cantidad.debe.tener.m.ximo.}")
    @Schema(description = "Cantidad a sumar o restar (negativa para restar)", example = "10.500")
    private BigDecimal quantityDelta;

    @NotNull(message = "{stockmovementitemdto.notnull.el.tipo.de.movimiento.es.oblig}")
    @Schema(description = "Tipo de movimiento", example = "AJUSTE")
    private MovementType movementType;

    @Size(max = 500, message = "{stockmovementitemdto.size.la.descripci.n.no.puede.excede}")
    @Schema(description = "Descripción del movimiento", example = "Rollback de receta errónea #123")
    private String description;
}
