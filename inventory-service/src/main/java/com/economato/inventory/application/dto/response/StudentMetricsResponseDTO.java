package com.economato.inventory.application.dto.response;

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

    public StudentMetricsResponseDTO(Integer studentId, String studentName, Long totalAssignments, Long totalConfirmed, Long totalCancelled, Double participationRate) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.totalAssignments = totalAssignments;
        this.totalConfirmed = totalConfirmed;
        this.totalCancelled = totalCancelled;
        this.participationRate = participationRate;
    }
}
