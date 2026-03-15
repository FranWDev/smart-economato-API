package com.economato.inventory.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para el reporte de trazabilidad inversa")
public class ReverseTraceabilityDTO {

    @Schema(description = "Datos de la auditoría de cocina original")
    private RecipeCookingAuditResponseDTO cookingAudit;

    @Schema(description = "Traza detallada de cada ingrediente utilizado")
    private List<IngredientTraceDTO> ingredientTrace;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Detalle del origen de un ingrediente específico")
    public static class IngredientTraceDTO {
        @Schema(description = "Nombre del producto")
        private String productName;
        @Schema(description = "Nombre del proveedor de origen")
        private String supplierName;
        @Schema(description = "ID del pedido de compra")
        private Integer orderId;
        @Schema(description = "Fecha de creación del pedido")
        private LocalDateTime orderDate;
        @Schema(description = "Usuario receptor del pedido")
        private String orderUserName;
        @Schema(description = "Tipo de movimiento")
        private String movementType;
        @Schema(description = "Descripción del movimiento")
        private String description;
        @Schema(description = "Hash criptográfico en el libro mayor")
        private String ledgerHash;
    }
}
