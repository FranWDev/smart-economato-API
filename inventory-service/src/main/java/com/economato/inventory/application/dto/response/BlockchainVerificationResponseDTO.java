package com.economato.inventory.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resultado de la verificación global de la blockchain del ledger")
public class BlockchainVerificationResponseDTO {

    @Schema(description = "¿La cadena completa es válida?", example = "true")
    private boolean valid;

    @Schema(description = "Mensaje descriptivo", example = "Blockchain íntegra: 4 bloques verificados")
    private String message;

    @Schema(description = "Total de bloques", example = "4")
    private long blockCount;

    @Schema(description = "Transacciones pendientes en mempool", example = "3")
    private long pendingTransactions;

    @Schema(description = "Último bloque confirmado", example = "3")
    private Long latestBlockNumber;

    @Schema(description = "Hash del último bloque confirmado", example = "0000ff12...")
    private String latestBlockHash;
}
