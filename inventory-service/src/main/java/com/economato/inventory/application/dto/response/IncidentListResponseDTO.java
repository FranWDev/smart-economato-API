package com.economato.inventory.application.dto.response;

import com.economato.inventory.domain.model.IncidentSeverity;
import com.economato.inventory.domain.model.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentListResponseDTO {
    private Long id;
    private IncidentTypeResponseDTO incidentType;
    private String title;
    private IncidentStatus status;
    private IncidentSeverity severity;
    private UserSummaryDTO createdBy;
    private UserSummaryDTO relatedTeacher;
    private LocalDateTime createdAt;
    private long chatMessageCount;
}
