package com.economatom.inventory.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO que representa una auditoría realizada sobre una orden")
public class OrderAuditResponseDTO {

    @Schema(description = "ID de la auditoría", example = "1")
    private Integer id;

    @Schema(description = "ID de la orden asociada a la auditoría", example = "12")
    private Integer orderId;

    @Schema(description = "ID del usuario que realizó la acción", example = "3")
    private Integer userId;

    @Schema(description = "Nombre del usuario que realizó la acción", example = "Juan Pérez")
    private String userName;

    @Schema(description = "Acción realizada sobre la orden", example = "CAMBIO DE ESTADO")
    private String action;

    @Schema(description = "Detalles de la acción realizada", example = "Estado cambiado de PENDING a CONFIRMED")
    private String details;

    @Schema(description = "Estado anterior de la orden en formato JSON", example = "{\"status\":\"PENDING\"}")
    private String previousState;

    @Schema(description = "Nuevo estado de la orden en formato JSON", example = "{\"status\":\"CONFIRMED\"}")
    private String newState;

    @Schema(description = "Fecha y hora en que se registró la auditoría", example = "2025-10-18T14:30:00")
    private LocalDateTime auditDate;
}
