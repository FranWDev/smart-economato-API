package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.request.ActivityLogPurgeRequestDTO;
import com.economato.inventory.application.dto.request.PresenceConfigRequestDTO;
import com.economato.inventory.application.dto.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.response.PresenceConfigResponseDTO;
import com.economato.inventory.application.dto.response.PurgeResultResponseDTO;
import com.economato.inventory.application.usecase.SystemConfigService;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserActivityLogRepository;
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
    private final UserActivityLogRepository userActivityLogRepository;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de presencia")
    public ResponseEntity<PresenceConfigResponseDTO> getPresenceConfig() {
        var c = systemConfigService.getConfigEntity();
        return ResponseEntity.ok(PresenceConfigResponseDTO.builder()
                .presenceAuditEnabled(c.isPresenceAuditEnabled())
                .presenceAutoCleanupEnabled(c.isPresenceAutoCleanupEnabled())
                .presenceAutoCleanupDays(c.getPresenceAutoCleanupDays())
                .totalLogCount(userActivityLogRepository.count())
                .build());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de presencia")
    public ResponseEntity<PresenceConfigResponseDTO> updatePresenceConfig(@Valid @RequestBody PresenceConfigRequestDTO request,
                                                                          Authentication authentication) {
        var c = systemConfigService.updatePresenceConfig(request, authentication.getName());
        return ResponseEntity.ok(PresenceConfigResponseDTO.builder()
                .presenceAuditEnabled(c.isPresenceAuditEnabled())
                .presenceAutoCleanupEnabled(c.isPresenceAutoCleanupEnabled())
                .presenceAutoCleanupDays(c.getPresenceAutoCleanupDays())
                .totalLogCount(userActivityLogRepository.count())
                .build());
    }

    @DeleteMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Purgar logs de actividad")
    public ResponseEntity<PurgeResultResponseDTO> purgeActivityLogs(@RequestBody(required = false) ActivityLogPurgeRequestDTO request,
                                                                    Authentication authentication) {
        int deleted = systemConfigService.purgeActivityLogs(request == null ? null : request.getFrom(), request == null ? null : request.getTo());
        return ResponseEntity.ok(PurgeResultResponseDTO.builder().deletedCount(deleted).build());
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de presencia")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        Page<ConfigAuditLogResponseDTO> page = systemConfigService.getAuditByCategoryDto("PRESENCE", pageable);
        return ResponseEntity.ok(page);
    }
}
