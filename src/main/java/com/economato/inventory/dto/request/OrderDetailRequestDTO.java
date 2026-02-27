package com.economato.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos requeridos para crear o actualizar un detalle de pedido")
public class OrderDetailRequestDTO {

    @NotNull
    @Schema(description = "Identificador del pedido asociado al detalle", example = "15")
    private Integer orderId;

    @NotNull(message = "{validation.orderDetailRequestDTO.productId.notNull}")
    @Schema(description = "Identificador del producto asociado al detalle del pedido", example = "42")
    private Integer productId;

    @NotNull(message = "{validation.orderDetailRequestDTO.quantity.notNull}")
    @DecimalMin(value = "0.001", message = "{validation.orderDetailRequestDTO.quantity.decimalMin}")
    @Digits(integer = 10, fraction = 3, message = "{validation.orderDetailRequestDTO.quantity.digits}")
    @Schema(description = "Cantidad del producto solicitada en el pedido", example = "3.5")
    private BigDecimal quantity;

    
}
