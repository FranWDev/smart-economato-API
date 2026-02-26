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

    @NotNull(message = "{orderreceptiondetailrequestdto.notnull.el.id.del.producto.no.puede.se}")
    @Schema(description = "Identificador del producto", example = "42")
    private Integer productId;

    @NotNull(message = "{orderreceptiondetailrequestdto.notnull.la.cantidad.recibida.no.puede.}")
    @PositiveOrZero(message = "{orderreceptiondetailrequestdto.positiveorzero.la.cantidad.recibida.debe.ser.}")
    @Schema(description = "Cantidad del producto recibida", example = "5.0")
    private BigDecimal quantityReceived;
}
