package com.economatom.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resultado de una operación batch de movimientos de stock")
public class BatchStockMovementResponseDTO {

    @Schema(description = "Indica si toda la operación fue exitosa", example = "true")
    private boolean success;

    @Schema(description = "Número de movimientos procesados exitosamente", example = "5")
    private int processedCount;

    @Schema(description = "Número total de movimientos solicitados", example = "5")
    private int totalCount;

    @Schema(description = "Mensaje descriptivo del resultado")
    private String message;

    @Schema(description = "Lista de transacciones creadas en el ledger")
    private List<StockLedgerResponseDTO> transactions;

    @Schema(description = "Mensaje de error si la operación falló")
    private String errorDetail;
}
