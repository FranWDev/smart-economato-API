package com.economato.inventory.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
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
@Schema(description = "Respuesta detallada tras la activación o consulta de una crisis")
public class CrisisResponseDTO {

    @Schema(description = "ID de la crisis en base de datos", example = "7")
    private Long crisisId;

    @Schema(description = "Código público de la crisis", example = "CRISIS-1A2B3C4D")
    private String crisisCode;

    @Schema(description = "Estado actual de la crisis", example = "ACTIVE")
    private String status;

    @Schema(description = "Motivo de la crisis")
    private String reason;

    @Schema(description = "Nombre del proveedor afectado")
    private String supplierName;

    @Schema(description = "Mapa de productos en cuarentena (Nombre -> Hash de transacción)")
    private Map<String, String> quarantinedProducts;

    @Schema(description = "Lotes identificados para los productos en crisis")
    private List<CrisisAffectedBatchDTO> affectedBatches;

    @Schema(description = "Lista de IDs de pedidos afectados")
    private List<Integer> affectedOrderIds;

    @Schema(description = "Lista de IDs de auditorías de cocina afectadas")
    private List<Long> affectedCookingAuditIds;

    @Schema(description = "Indica si la integridad de la cadena de bloques ha sido verificada")
    private boolean integrityVerified;

    @Schema(description = "Resumen de la trazabilidad realizada")
    private String summary;

    @Schema(description = "Fecha y hora del registro")
    private LocalDateTime timestamp;
}
