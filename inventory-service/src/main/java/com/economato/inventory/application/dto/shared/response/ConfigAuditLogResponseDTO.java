package com.economato.inventory.application.dto.shared.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigAuditLogResponseDTO {
    private String username;
    private String category;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private LocalDateTime changedAt;
}
