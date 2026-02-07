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

    @NotNull(message = "El ID del producto no puede ser nulo")
    @Schema(description = "Identificador del producto asociado al detalle del pedido", example = "42")
    private Integer productId;

    @NotNull(message = "La cantidad no puede ser nula")
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
    @Digits(integer = 10, fraction = 3, message = "La cantidad debe tener máximo 10 dígitos enteros y 3 decimales")
    @Schema(description = "Cantidad del producto solicitada en el pedido", example = "3.5")
    private BigDecimal quantity;

    
}
