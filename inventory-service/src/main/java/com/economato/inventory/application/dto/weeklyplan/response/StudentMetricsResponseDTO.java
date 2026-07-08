package com.economato.inventory.application.dto.weeklyplan.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentMetricsResponseDTO {
    private Integer studentId;
    private String studentName;
    private Long totalAssignments;
    private Long totalConfirmed;
    private Long totalCancelled;
    private Double participationRate;
}
