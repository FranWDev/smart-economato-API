package com.economato.inventory.application.dto.incident.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentAuditAttachmentResponseDTO {
    private Long id;
    private Long cookingAuditId;
    private String recipeName;
    private LocalDateTime cookingDate;
    private String userName;
    private BigDecimal quantityCooked;
    private boolean reverted;
    private LocalDateTime revertedAt;
}
