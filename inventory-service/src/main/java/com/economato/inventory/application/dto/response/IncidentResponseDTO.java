package com.economato.inventory.application.dto.response;

import com.economato.inventory.domain.model.IncidentSeverity;
import com.economato.inventory.domain.model.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentResponseDTO {
    private Long id;
    private IncidentTypeResponseDTO incidentType;
    private String title;
    private String description;
    private IncidentStatus status;
    private IncidentSeverity severity;
    private UserSummaryDTO createdBy;
    private UserSummaryDTO relatedTeacher;
    private String resolution;
    private LocalDateTime openedAt;
    private UserSummaryDTO openedBy;
    private LocalDateTime closedAt;
    private UserSummaryDTO closedBy;
    private LocalDateTime createdAt;
    private List<IncidentAuditAttachmentResponseDTO> attachedAudits;
    private long chatMessageCount;
}
