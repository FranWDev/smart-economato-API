package com.economato.inventory.application.dto.ledger.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Bloque del ledger blockchain")
public class LedgerBlockResponseDTO {

    @Schema(description = "Número de bloque", example = "12")
    private Long blockNumber;

    @Schema(description = "Hash del bloque anterior", example = "0000abcd...")
    private String previousBlockHash;

    @Schema(description = "Raíz de Merkle", example = "f1e2d3c4...")
    private String merkleRoot;

    @Schema(description = "Hash del bloque", example = "0000ff12...")
    private String blockHash;


    @Schema(description = "Marca temporal del bloque")
    private LocalDateTime timestamp;

    @Schema(description = "Número de transacciones incluidas", example = "10")
    private Integer transactionCount;

    @Schema(description = "Versión de la clave HMAC", example = "1")
    private Integer hmacKeyVersion;
}
