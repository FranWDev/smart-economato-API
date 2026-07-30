package com.economato.user.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resultado de la operación batch de asignación de profesor")
public class BatchTeacherAssignmentResponseDTO {

    private boolean success;
    private int processedCount;
    private int totalCount;
    private String message;
    private List<Integer> failedStudentIds;
}
