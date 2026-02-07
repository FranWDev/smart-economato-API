package com.economato.inventory.dto.request;

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

    @NotNull(message = "El ID de la orden no puede ser nulo")
    @Schema(description = "Identificador de la orden a recibir", example = "5")
    private Integer orderId;

    @NotEmpty(message = "Debe especificar al menos un producto recibido")
    @Schema(description = "Lista de productos recibidos con sus cantidades")
    private List<OrderReceptionDetailRequestDTO> items;

    @NotNull(message = "El estado debe ser especificado")
    @Schema(description = "Estado final de la orden (CONFIRMED o INCOMPLETE)", example = "CONFIRMED")
    private String status;
}
