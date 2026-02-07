package com.economato.inventory.dto.response;

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
@Schema(description = "Transacción del ledger de stock inmutable")
public class StockLedgerResponseDTO {

    @Schema(description = "ID de la transacción", example = "1")
    private Long id;

    @Schema(description = "ID del producto", example = "42")
    private Integer productId;

    @Schema(description = "Nombre del producto", example = "Harina de trigo")
    private String productName;

    @Schema(description = "Delta de cantidad (+entrada/-salida)", example = "10.500")
    private BigDecimal quantityDelta;

    @Schema(description = "Stock resultante después de la transacción", example = "100.500")
    private BigDecimal resultingStock;

    @Schema(description = "Tipo de movimiento", example = "PURCHASE")
    private String movementType;

    @Schema(description = "Descripción del movimiento", example = "Recepción de pedido #123")
    private String description;

    @Schema(description = "Hash de la transacción anterior", example = "a3f5d8e9...")
    private String previousHash;

    @Schema(description = "Hash de esta transacción", example = "b2e4f6c1...")
    private String currentHash;

    @Schema(description = "Timestamp de la transacción")
    private LocalDateTime transactionTimestamp;

    @Schema(description = "Número de secuencia", example = "5")
    private Long sequenceNumber;

    @Schema(description = "Usuario que realizó la transacción", example = "admin")
    private String userName;

    @Schema(description = "ID del pedido relacionado", example = "123")
    private Integer orderId;

    @Schema(description = "Transacción verificada", example = "true")
    private Boolean verified;
}
