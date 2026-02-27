package com.economato.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO utilizado para registrar un nuevo movimiento de inventario (entrada o salida).")
public class InventoryMovementRequestDTO {

    @NotNull(message = "{validation.inventoryMovementRequestDTO.productId.notNull}")
    @Schema(description = "Identificador único del producto afectado por el movimiento", example = "12")
    private Integer productId;

    @NotNull(message = "{validation.inventoryMovementRequestDTO.userId.notNull}")
    @Schema(description = "ID del usuario responsable del movimiento de inventario", example = "5")
    private Integer userId;

    @NotNull(message = "{validation.inventoryMovementRequestDTO.quantity.notNull}")
    @Positive(message = "{validation.inventoryMovementRequestDTO.quantity.positive}")
    @DecimalMax(value = "9999999.99", message = "{validation.inventoryMovementRequestDTO.quantity.decimalMax}")
    @Schema(description = "Cantidad de producto que entra o sale del inventario", example = "50.75")
    private BigDecimal quantity;

    @NotNull(message = "{validation.inventoryMovementRequestDTO.movementType.notNull}")
    @Pattern(regexp = "^(ENTRADA|SALIDA)$", message = "El tipo de movimiento debe ser ENTRADA o SALIDA")
    @Schema(description = "Tipo de movimiento de inventario (ENTRADA o SALIDA)", example = "ENTRADA")
    private String movementType;
}
