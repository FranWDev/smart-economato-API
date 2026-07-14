package com.economato.inventory.infrastructure.adapter.in.web.notification.config;

import com.economato.inventory.application.dto.shared.request.ActivityLogPurgeRequestDTO;
import com.economato.inventory.application.dto.notification.request.PresenceConfigRequestDTO;
import com.economato.inventory.application.dto.shared.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.notification.response.PresenceConfigResponseDTO;
import com.economato.inventory.application.dto.shared.response.PurgeResultResponseDTO;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
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
@RequestMapping("/api/config/presence")
@RequiredArgsConstructor
@Tag(name = "Configuración de Presencia", description = "Gestión de configuración de presencia y limpieza de logs")
public class PresenceConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de presencia")
    public ResponseEntity<PresenceConfigResponseDTO> getPresenceConfig() {
        return ResponseEntity.ok(systemConfigService.getPresenceConfigDto());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de presencia")
    public ResponseEntity<PresenceConfigResponseDTO> updatePresenceConfig(@Valid @RequestBody PresenceConfigRequestDTO request,
                                                                          Authentication authentication) {
        return ResponseEntity.ok(systemConfigService.updatePresenceConfigDto(request, authentication.getName()));
    }

    @DeleteMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Purgar logs de actividad")
    public ResponseEntity<PurgeResultResponseDTO> purgeActivityLogs(@RequestBody(required = false) ActivityLogPurgeRequestDTO request,
                                                                    Authentication authentication) {
        return ResponseEntity.ok(systemConfigService.purgeActivityLogsDto(request));
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de presencia")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategoryDto("PRESENCE", pageable));
    }
}
