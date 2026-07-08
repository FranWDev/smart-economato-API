package com.economato.inventory.application.dto.order.request;
import com.economato.inventory.application.dto.shared.request.LotReceptionRequestDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para recibir un producto dentro de una orden")
public class OrderReceptionDetailRequestDTO {

    @NotNull(message = "{validation.orderReceptionDetailRequestDTO.productId.notNull}")
    @Schema(description = "Identificador del producto", example = "42")
    private Integer productId;

    @NotNull(message = "{validation.orderReceptionDetailRequestDTO.quantityReceived.notNull}")
    @PositiveOrZero(message = "{validation.orderReceptionDetailRequestDTO.quantityReceived.positiveOrZero}")
    @Schema(description = "Cantidad del producto recibida", example = "5.0")
    private BigDecimal quantityReceived;

    @NotEmpty(message = "{validation.orderReceptionDetailRequestDTO.lots.notEmpty}")
    @Valid
    @Schema(description = "Lista de lotes recibidos con sus cantidades y fechas de caducidad")
    private List<LotReceptionRequestDTO> lots;
}
