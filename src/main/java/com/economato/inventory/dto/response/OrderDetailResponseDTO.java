package com.economato.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detalle de pedido con información del producto, cantidades y totales")
public class OrderDetailResponseDTO {

    @Schema(description = "ID del pedido", example = "10")
    private Integer orderId;

    @Schema(description = "Identificador del producto", example = "42")
    private Integer productId;

    @Schema(description = "Nombre del producto", example = "Tomate triturado 500g")
    private String productName;

    @Schema(description = "Cantidad del producto en el pedido", example = "3.5")
    private BigDecimal quantity;

    @Schema(description = "Cantidad recibida del producto", example = "3.5")
    private BigDecimal quantityReceived;

    @Schema(description = "Precio unitario del producto en el pedido", example = "1.20")
    private BigDecimal unitPrice;

    @Schema(description = "Subtotal del detalle (cantidad × precio unitario)", example = "4.20")
    private BigDecimal subtotal;
}
