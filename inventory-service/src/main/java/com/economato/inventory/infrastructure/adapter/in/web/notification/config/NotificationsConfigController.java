package com.economato.inventory.infrastructure.adapter.in.web.notification.config;

import com.economato.inventory.application.dto.notification.request.NotificationPurgeRequestDTO;
import com.economato.inventory.application.dto.notification.request.NotificationsConfigRequestDTO;
import com.economato.inventory.application.dto.shared.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.notification.response.NotificationsConfigResponseDTO;
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
@RequestMapping("/api/config/notifications")
@RequiredArgsConstructor
@Tag(name = "Configuración de Notificaciones", description = "Gestión de tipos de notificaciones y limpieza")
public class NotificationsConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de notificaciones")
    public ResponseEntity<NotificationsConfigResponseDTO> get() {
        return ResponseEntity.ok(systemConfigService.getNotificationsConfigDto());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de notificaciones")
    public ResponseEntity<NotificationsConfigResponseDTO> update(@Valid @RequestBody NotificationsConfigRequestDTO request,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(systemConfigService.updateNotificationsConfigDto(request, authentication.getName()));
    }

    @DeleteMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Purgar notificaciones leídas")
    public ResponseEntity<PurgeResultResponseDTO> purge(@RequestBody(required = false) NotificationPurgeRequestDTO request) {
        return ResponseEntity.ok(systemConfigService.purgeReadNotificationsDto(request));
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de notificaciones")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategoryDto("NOTIFICATIONS", pageable));
    }
}