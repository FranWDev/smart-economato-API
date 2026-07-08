package com.economato.inventory.application.dto.product.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos de respuesta de un lote de producto")
public class ProductBatchResponseDTO {

    @Schema(description = "ID del lote", example = "10")
    private Long id;

    @Schema(description = "ID del producto", example = "42")
    private Integer productId;

    @Schema(description = "Nombre del producto", example = "Leche")
    private String productName;

    @Schema(description = "Fecha de caducidad del lote", example = "2026-12-31")
    private LocalDate expirationDate;

    @Schema(description = "Cantidad inicial del lote", example = "20.000")
    private BigDecimal initialQuantity;

    @Schema(description = "Cantidad restante del lote", example = "8.500")
    private BigDecimal remainingQuantity;

    @Schema(description = "Fecha/hora de recepción del lote")
    private LocalDateTime receivedAt;

    @Schema(description = "Código identificativo del lote", example = "LOT-2026-001")
    private String batchCode;

    @Schema(description = "Indica si el lote está agotado", example = "false")
    private boolean depleted;

    @Schema(description = "Indica si el lote está caducado", example = "false")
    private boolean expired;

    @Schema(description = "Días restantes para caducar (0 si ya caducó o si no aplica)", example = "14")
    private long daysUntilExpiration;
}
