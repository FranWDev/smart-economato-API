package com.economato.inventory.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevertAuditFromIncidentRequestDTO {

    @NotNull(message = "{validation.revertAuditFromIncidentRequestDTO.auditAttachmentId.notNull}")
    private Long auditAttachmentId;

    @NotBlank(message = "{validation.revertAuditFromIncidentRequestDTO.reason.notBlank}")
    private String reason;
}
