package com.economato.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representa la respuesta detallada de un pedido, incluyendo usuario, estado, fecha y detalles.")
public class OrderResponseDTO {

    @Schema(description = "Identificador único del pedido", example = "25")
    private Integer id;

    @Schema(description = "ID del usuario asociado al pedido", example = "4")
    private Integer userId;

    @Schema(description = "Nombre del usuario que realizó el pedido", example = "Carlos Pérez")
    private String userName;

    @Schema(description = "Fecha y hora en que se realizó el pedido", example = "2025-03-21T14:35:00")
    private LocalDateTime orderDate;

    @Schema(description = "Estado actual del pedido", example = "CREATED")
    private String status;

    @Schema(description = "Precio total del pedido (suma de todos los detalles)", example = "150.50")
    private BigDecimal totalPrice;

    @Schema(description = "Lista de detalles (productos, cantidades y subtotales)")
    private List<OrderDetailResponseDTO> details;

    /**
     * Constructor alternativo sin totalPrice (para compatibilidad)
     */
    public OrderResponseDTO(Integer id, Integer userId, String userName, LocalDateTime orderDate, String status, List<OrderDetailResponseDTO> details) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.orderDate = orderDate;
        this.status = status;
        this.details = details;
        this.totalPrice = calculateTotalPrice(details);
    }

    /**
     * Calcula el precio total a partir de los detalles
     */
    private static BigDecimal calculateTotalPrice(List<OrderDetailResponseDTO> details) {
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return details.stream()
                .map(OrderDetailResponseDTO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
