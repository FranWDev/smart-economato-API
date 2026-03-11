package com.economato.inventory.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resultado de la operación batch de asignación de profesor")
public class BatchTeacherAssignmentResponseDTO {

    @Schema(description = "Indica si toda la operación fue exitosa", example = "true")
    private boolean success;

    @Schema(description = "Número de alumnos procesados exitosamente", example = "3")
    private int processedCount;

    @Schema(description = "Número total de alumnos en la solicitud", example = "3")
    private int totalCount;

    @Schema(description = "Mensaje descriptivo del resultado", example = "Todos los alumnos fueron asignados correctamente")
    private String message;

    @Schema(description = "IDs de los alumnos que no pudieron ser asignados (vacío si todo fue exitoso)")
    private List<Integer> failedStudentIds;
}
