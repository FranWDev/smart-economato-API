package com.economato.user.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para asignar un profesor a múltiples alumnos en un batch")
public class BatchTeacherAssignmentRequestDTO {

    @Schema(description = "ID del profesor a asignar", example = "2")
    private Integer teacherId;

    @NotEmpty(message = "{validation.batchTeacherAssignmentRequestDTO.studentIds.notEmpty}")
    @Schema(description = "Lista de IDs de alumnos", example = "[3, 4, 5]")
    private List<Integer> studentIds;
}
