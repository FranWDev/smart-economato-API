package com.economato.inventory.application.mapper.incident;
import com.economato.inventory.application.dto.incident.response.IncidentAuditAttachmentResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentListResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentResponseDTO;
import com.economato.inventory.application.dto.user.response.UserSummaryDTO;


import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.incident.IncidentAuditAttachment;
import com.economato.inventory.domain.model.user.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncidentMapper {

    private final IncidentTypeMapper incidentTypeMapper;

    public IncidentMapper(IncidentTypeMapper incidentTypeMapper) {
        this.incidentTypeMapper = incidentTypeMapper;
    }

    public IncidentListResponseDTO toListResponseDTO(Incident incident, long chatMessageCount) {
        if (incident == null) {
            return null;
        }

        return IncidentListResponseDTO.builder()
                .id(incident.getId())
                .incidentType(incidentTypeMapper.toResponseDTO(incident.getIncidentType()))
                .title(incident.getTitle())
                .status(incident.getStatus())
                .severity(incident.getSeverity())
                .createdBy(toUserSummary(incident.getCreatedBy()))
                .relatedTeacher(toUserSummary(incident.getRelatedTeacher()))
                .createdAt(incident.getCreatedAt())
                .chatMessageCount(chatMessageCount)
                .build();
    }

    public IncidentResponseDTO toResponseDTO(Incident incident,
                                             List<IncidentAuditAttachment> attachments,
                                             long chatMessageCount) {
        if (incident == null) {
            return null;
        }

        List<IncidentAuditAttachmentResponseDTO> attachedAudits = attachments == null ? List.of() : attachments.stream()
                .map(this::toAttachmentResponseDTO)
                .toList();

        return IncidentResponseDTO.builder()
                .id(incident.getId())
                .incidentType(incidentTypeMapper.toResponseDTO(incident.getIncidentType()))
                .title(incident.getTitle())
                .description(incident.getDescription())
                .status(incident.getStatus())
                .severity(incident.getSeverity())
                .createdBy(toUserSummary(incident.getCreatedBy()))
                .relatedTeacher(toUserSummary(incident.getRelatedTeacher()))
                .resolution(incident.getResolution())
                .openedAt(incident.getOpenedAt())
                .openedBy(toUserSummary(incident.getOpenedBy()))
                .closedAt(incident.getClosedAt())
                .closedBy(toUserSummary(incident.getClosedBy()))
                .createdAt(incident.getCreatedAt())
                .attachedAudits(attachedAudits)
                .chatMessageCount(chatMessageCount)
                .build();
    }

    public IncidentAuditAttachmentResponseDTO toAttachmentResponseDTO(IncidentAuditAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        return IncidentAuditAttachmentResponseDTO.builder()
                .id(attachment.getId())
                .cookingAuditId(attachment.getCookingAudit() != null ? attachment.getCookingAudit().getId() : null)
                .recipeName(attachment.getCookingAudit() != null && attachment.getCookingAudit().getRecipe() != null
                        ? attachment.getCookingAudit().getRecipe().getName() : null)
                .cookingDate(attachment.getCookingAudit() != null ? attachment.getCookingAudit().getCookingDate() : null)
                .userName(attachment.getCookingAudit() != null && attachment.getCookingAudit().getUser() != null
                        ? attachment.getCookingAudit().getUser().getName() : null)
                .quantityCooked(attachment.getCookingAudit() != null ? attachment.getCookingAudit().getQuantityCooked() : null)
                .reverted(attachment.isReverted())
                .revertedAt(attachment.getRevertedAt())
                .build();
    }

    private UserSummaryDTO toUserSummary(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryDTO(user.getId(), user.getName(), user.getUser(), user.getRole());
    }
}
