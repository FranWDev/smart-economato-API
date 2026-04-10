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
@Schema(description = "Estadísticas resumidas de la blockchain del ledger")
public class BlockchainStatsResponseDTO {

    @Schema(description = "Total de bloques persistidos", example = "42")
    private long blockCount;

    @Schema(description = "Transacciones pendientes en el mempool", example = "7")
    private long pendingTransactions;

    @Schema(description = "Número del último bloque", example = "41")
    private Long latestBlockNumber;

    @Schema(description = "Hash del último bloque", example = "0000abcd...")
    private String latestBlockHash;

    @Schema(description = "Dificultad configurada", example = "2")
    private Integer difficulty;

    @Schema(description = "¿La cadena parece íntegra?", example = "true")
    private boolean valid;
}
