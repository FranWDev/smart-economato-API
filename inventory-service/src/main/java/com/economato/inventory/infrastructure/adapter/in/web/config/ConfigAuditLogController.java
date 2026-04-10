package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.usecase.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/audit-log")
@RequiredArgsConstructor
@Tag(name = "Auditoría de Configuración", description = "Historial global de cambios de configuración")
public class ConfigAuditLogController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener historial global de configuración")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> getGlobalAudit(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditGlobal(pageable)
                .map(log -> ConfigAuditLogResponseDTO.builder()
                        .username(log.getUser() != null ? log.getUser().getName() : null)
                        .category(log.getCategory())
                        .fieldName(log.getFieldName())
                        .oldValue(log.getOldValue())
                        .newValue(log.getNewValue())
                        .changedAt(log.getChangedAt())
                        .build()));
    }
}
