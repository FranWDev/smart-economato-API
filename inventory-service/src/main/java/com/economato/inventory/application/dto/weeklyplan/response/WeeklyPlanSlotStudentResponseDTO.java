package com.economato.inventory.application.dto.weeklyplan.response;

import com.economato.inventory.domain.model.weeklyplan.StudentSlotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanSlotStudentResponseDTO {
    private Long id;
    private Integer studentId;
    private String studentName;
    private StudentSlotStatus status;
    private LocalDateTime cancelledAt;
    private String cancelledByName;
}
