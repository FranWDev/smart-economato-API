package com.economato.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para recibir un producto dentro de una orden")
public class OrderReceptionDetailRequestDTO {

    @NotNull(message = "El ID del producto no puede ser nulo")
    @Schema(description = "Identificador del producto", example = "42")
    private Integer productId;

    @NotNull(message = "La cantidad recibida no puede ser nula")
    @PositiveOrZero(message = "La cantidad recibida debe ser mayor o igual a cero")
    @Schema(description = "Cantidad del producto recibida", example = "5.0")
    private BigDecimal quantityReceived;
}
