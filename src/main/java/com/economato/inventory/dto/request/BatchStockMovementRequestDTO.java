package com.economato.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Operación batch de movimientos de stock")
public class BatchStockMovementRequestDTO {

    @NotEmpty(message = "{validation.batchStockMovementRequestDTO.movements.notEmpty}")
    @Valid
    @Schema(description = "Lista de movimientos de stock a procesar")
    private List<StockMovementItemDTO> movements;

    @Size(max = 1000, message = "{validation.batchStockMovementRequestDTO.reason.size}")
    @Schema(description = "Razón general de la operación batch", example = "Rollback de orden #456 - productos recibidos incorrectamente")
    private String reason;

    @Schema(description = "ID de la orden asociada (opcional)", example = "456")
    private Integer orderId;
}
