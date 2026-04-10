package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.request.IncidentsConfigRequestDTO;
import com.economato.inventory.application.dto.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.response.IncidentsConfigResponseDTO;
import com.economato.inventory.application.usecase.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/incidents")
@RequiredArgsConstructor
@Tag(name = "Configuración de Incidencias", description = "Gestión de límites de incidencias y archivos")
public class IncidentsConfigController {
    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de incidencias")
    public ResponseEntity<IncidentsConfigResponseDTO> get() {
        var c = systemConfigService.getConfigEntity();
        return ResponseEntity.ok(IncidentsConfigResponseDTO.builder()
                .maxChatMessageLength(c.getMaxChatMessageLength())
                .maxAdminAttachableAudits(c.getMaxAdminAttachableAudits())
                .maxUploadFileSizeBytes(c.getMaxUploadFileSizeBytes())
                .allowedFileTypes(c.getAllowedFileTypes())
                .build());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de incidencias")
    public ResponseEntity<IncidentsConfigResponseDTO> update(@Valid @RequestBody IncidentsConfigRequestDTO request,
                                                             Authentication authentication) {
        var c = systemConfigService.updateIncidentsConfig(request, authentication.getName());
        return ResponseEntity.ok(IncidentsConfigResponseDTO.builder()
                .maxChatMessageLength(c.getMaxChatMessageLength())
                .maxAdminAttachableAudits(c.getMaxAdminAttachableAudits())
                .maxUploadFileSizeBytes(c.getMaxUploadFileSizeBytes())
                .allowedFileTypes(c.getAllowedFileTypes())
                .build());
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de incidencias")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategory("INCIDENTS", pageable)
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
