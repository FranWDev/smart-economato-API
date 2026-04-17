package com.economato.inventory.application.dto.response;

import com.economato.inventory.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cantidad de un producto dentro de un pedido concreto")
public class ProductOrderQuantityResponseDTO {

    @Schema(description = "ID del pedido", example = "42")
    private Integer orderId;

    @Schema(description = "Estado del pedido", example = "PENDING")
    private OrderStatus status;

    @Schema(description = "Cantidad del producto en ese pedido", example = "2.5")
    private BigDecimal quantity;

    @Schema(description = "Proveedor del pedido", example = "Proveedor Norte")
    private String supplierName;

    @Schema(description = "Fecha del pedido", example = "2026-04-17T10:00:00")
    private LocalDateTime orderDate;
}
