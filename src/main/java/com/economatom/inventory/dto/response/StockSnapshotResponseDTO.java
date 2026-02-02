package com.economatom.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Snapshot del stock actual de un producto")
public class StockSnapshotResponseDTO {

    @Schema(description = "ID del producto", example = "42")
    private Integer productId;

    @Schema(description = "Nombre del producto", example = "Harina de trigo")
    private String productName;

    @Schema(description = "Stock actual", example = "100.500")
    private BigDecimal currentStock;

    @Schema(description = "Hash de la última transacción", example = "b2e4f6c1...")
    private String lastTransactionHash;

    @Schema(description = "Número de secuencia de la última transacción", example = "5")
    private Long lastSequenceNumber;

    @Schema(description = "Última actualización del snapshot")
    private LocalDateTime lastUpdated;

    @Schema(description = "Última verificación de integridad")
    private LocalDateTime lastVerified;

    @Schema(description = "Estado de integridad", example = "VALID", allowableValues = {"VALID", "CORRUPTED", "UNVERIFIED"})
    private String integrityStatus;
}
