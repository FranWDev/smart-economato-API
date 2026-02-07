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
@Schema(description = "DTO utilizado para crear o actualizar un pedido.")
public class OrderRequestDTO {

    @NotNull(message = "El ID del usuario no puede ser nulo")
    @Schema(description = "Identificador único del usuario asociado al pedido", example = "4")
    private Integer userId;

    @NotEmpty(message = "El pedido debe tener al menos un detalle")
    @Schema(description = "Lista de productos y cantidades incluidas en el pedido")
    private List<OrderDetailRequestDTO> details;
}
