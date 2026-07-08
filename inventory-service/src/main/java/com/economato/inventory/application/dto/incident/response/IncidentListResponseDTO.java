package com.economato.inventory.application.dto.incident.response;
import com.economato.inventory.application.dto.user.response.UserSummaryDTO;

import com.economato.inventory.domain.model.incident.IncidentSeverity;
import com.economato.inventory.domain.model.incident.IncidentStatus;
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
