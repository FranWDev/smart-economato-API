package com.economato.inventory.application.dto.order.request;

import com.economato.inventory.domain.model.order.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filtro batch de pedidos por productos y estados")
public class OrdersByProductsRequestDTO {

    @NotEmpty(message = "La lista de productos no puede estar vacia")
    @Schema(description = "IDs de productos a consultar", example = "[1,2,3]")
    private List<Integer> productIds;

    @Schema(description = "Estados de pedido a incluir. Si no se envian, se usan CREATED, PENDING y REVIEW")
    private List<OrderStatus> statuses;
}
