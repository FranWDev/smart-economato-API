package com.economato.inventory.application.dto.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta batch de pedidos vinculados a productos")
public class OrdersByProductsResponseDTO {

    @Schema(description = "Pedidos que contienen al menos uno de los productos solicitados")
    private List<OrderResponseDTO> orders;

    @Schema(description = "Cantidad total por producto en pedidos filtrados")
    private Map<Integer, BigDecimal> totalQuantityPerProduct;

    @Schema(description = "Numero de pedidos por producto")
    private Map<Integer, Integer> orderCountPerProduct;

    @Schema(description = "Detalle por producto y pedido")
    private Map<Integer, List<ProductOrderQuantityResponseDTO>> ordersByProduct;
}
