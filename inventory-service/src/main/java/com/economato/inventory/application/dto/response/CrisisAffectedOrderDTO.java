package com.economato.inventory.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen de pedido afectado durante una crisis")
public class CrisisAffectedOrderDTO {

    @Schema(description = "ID del pedido", example = "42")
    private Integer orderId;

    @Schema(description = "Nombre del proveedor", example = "Distribuciones García")
    private String supplierName;

    @Schema(description = "Estado del pedido", example = "CONFIRMED")
    private String status;

    @Schema(description = "Fecha de creación del pedido")
    private LocalDateTime createdAt;

    @Schema(description = "Número de líneas en el pedido", example = "3")
    private int totalItems;
}
