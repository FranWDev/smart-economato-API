package com.economato.inventory.application.dto.request;

import com.economato.inventory.domain.model.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de ajuste manual de stock sobre un producto, con lote destino opcional")
public class ManualStockAdjustmentRequestDTO {

    @NotNull(message = "El ID del producto es obligatorio")
    @Schema(description = "ID del producto a ajustar", example = "45", required = true)
    private Integer productId;

    @NotNull(message = "El delta de cantidad es obligatorio")
    @Schema(description = "Cantidad a añadir (positivo) o restar (negativo). Para SALIDA y MERMA introduce valor negativo.", example = "-2.500", required = true)
    private BigDecimal quantityDelta;

    @NotNull(message = "El tipo de movimiento es obligatorio")
    @Schema(description = "Tipo de movimiento: SALIDA, MERMA o MODIFICACION", example = "MERMA", required = true)
    private MovementType movementType;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    @Schema(description = "Descripción del motivo del ajuste", example = "Merma por deterioro detectado en inventario manual")
    private String description;

    @Schema(description = "ID del lote específico al que aplicar el ajuste. Si se omite, se aplica FIFO automático.", example = "12")
    private Long batchId;

    @Schema(description = "Fecha de caducidad para el lote. Obligatoria si se añade stock (delta positivo) sin batchId.", example = "2026-12-31")
    private LocalDate expirationDate;
}

