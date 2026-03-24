package com.economato.inventory.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelStudentRequestDTO {
    @NotNull(message = "{ValidationMessages.student_id_required}")
    private Integer studentId;
}
