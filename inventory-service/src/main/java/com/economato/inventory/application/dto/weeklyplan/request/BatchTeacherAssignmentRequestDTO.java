package com.economato.inventory.application.dto.weeklyplan.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para asignar un profesor a múltiples alumnos en un batch")
public class BatchTeacherAssignmentRequestDTO {

    @Schema(description = "ID del profesor (usuario con rol CHEF) a asignar. Enviar null para desasignar.", example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer teacherId;

    @NotEmpty(message = "{validation.batchTeacherAssignmentRequestDTO.studentIds.notEmpty}")
    @Schema(description = "Lista de IDs de alumnos a los que se asignará el profesor", example = "[3, 4, 5]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Integer> studentIds;
}
