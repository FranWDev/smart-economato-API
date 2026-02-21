package com.economato.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representa la información de un movimiento de inventario registrado en la auditoría.")
public class InventoryMovementResponseDTO {

    @Schema(description = "Identificador único del movimiento", example = "87")
    private Integer id;

    @Schema(description = "Identificador del producto afectado", example = "34")
    private Integer productId;

    @Schema(description = "Nombre del producto afectado", example = "Aceite de oliva")
    private String productName;

    @Schema(description = "Identificador del usuario responsable", example = "5")
    private Integer userId;

    @Schema(description = "Nombre del usuario responsable", example = "Lucía Gómez")
    private String userName;

    @Schema(description = "Cantidad movida en el inventario", example = "15.00")
    private BigDecimal quantity;

    @Schema(description = "Tipo de movimiento (ENTRADA o SALIDA)", example = "SALIDA")
    private String movementType;

    @Schema(description = "Fecha y hora en que se registró el movimiento", example = "2025-03-18T10:45:00")
    private LocalDateTime movementDate;

    @Schema(description = "Stock del producto antes del movimiento", example = "250.00")
    private BigDecimal previousStock;

    @Schema(description = "Stock actual del producto después del movimiento", example = "235.00")
    private BigDecimal currentStock;

    @Schema(description = "Estado JSON del producto antes de la acción")
    private String previousState;

    @Schema(description = "Estado JSON del producto después de la acción")
    private String newState;
}
