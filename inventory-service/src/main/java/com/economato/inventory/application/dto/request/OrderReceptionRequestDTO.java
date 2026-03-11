package com.economato.inventory.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para procesar la recepción de una orden")
public class OrderReceptionRequestDTO {

    @NotNull(message = "{validation.orderReceptionRequestDTO.orderId.notNull}")
    @Schema(description = "Identificador de la orden a recibir", example = "5")
    private Integer orderId;

    @NotEmpty(message = "{validation.orderReceptionRequestDTO.items.notEmpty}")
    @Schema(description = "Lista de productos recibidos con sus cantidades")
    private List<OrderReceptionDetailRequestDTO> items;

}
