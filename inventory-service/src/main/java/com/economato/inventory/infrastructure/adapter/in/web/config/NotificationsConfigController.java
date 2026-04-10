package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.request.NotificationPurgeRequestDTO;
import com.economato.inventory.application.dto.request.NotificationsConfigRequestDTO;
import com.economato.inventory.application.dto.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.response.NotificationsConfigResponseDTO;
import com.economato.inventory.application.dto.response.PurgeResultResponseDTO;
import com.economato.inventory.application.usecase.SystemConfigService;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.NotificationRepository;
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
    private final NotificationRepository notificationRepository;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de notificaciones")
    public ResponseEntity<NotificationsConfigResponseDTO> get() {
        var c = systemConfigService.getConfigEntity();
        return ResponseEntity.ok(toDto(c));
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de notificaciones")
    public ResponseEntity<NotificationsConfigResponseDTO> update(@Valid @RequestBody NotificationsConfigRequestDTO request,
                                                                 Authentication authentication) {
        var c = systemConfigService.updateNotificationsConfig(request, authentication.getName());
        return ResponseEntity.ok(toDto(c));
    }

    @DeleteMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Purgar notificaciones leídas")
    public ResponseEntity<PurgeResultResponseDTO> purge(@RequestBody(required = false) NotificationPurgeRequestDTO request) {
        int deleted = systemConfigService.purgeReadNotifications(request == null ? null : request.getFrom(), request == null ? null : request.getTo());
        return ResponseEntity.ok(PurgeResultResponseDTO.builder().deletedCount(deleted).build());
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de notificaciones")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategory("NOTIFICATIONS", pageable)
                .map(log -> ConfigAuditLogResponseDTO.builder()
                        .username(log.getUser() != null ? log.getUser().getName() : null)
                        .category(log.getCategory())
                        .fieldName(log.getFieldName())
                        .oldValue(log.getOldValue())
                        .newValue(log.getNewValue())
                        .changedAt(log.getChangedAt())
                        .build()));
    }

    private NotificationsConfigResponseDTO toDto(com.economato.inventory.domain.model.SystemConfig c) {
        return NotificationsConfigResponseDTO.builder()
                .notifyWeeklyPlanCreated(c.isNotifyWeeklyPlanCreated())
                .notifyWeeklyPlanActivated(c.isNotifyWeeklyPlanActivated())
                .notifyWeeklyPlanSlotConfirmed(c.isNotifyWeeklyPlanSlotConfirmed())
                .notifyWeeklyPlanDayConfirmed(c.isNotifyWeeklyPlanDayConfirmed())
                .notifyWeeklyPlanCompleted(c.isNotifyWeeklyPlanCompleted())
                .notifyWeeklyPlanCancelled(c.isNotifyWeeklyPlanCancelled())
                .notifyFoodCrisisActivated(c.isNotifyFoodCrisisActivated())
                .notifyFoodCrisisLifted(c.isNotifyFoodCrisisLifted())
                .notifyStockPredictionTriggered(c.isNotifyStockPredictionTriggered())
                .notifyIncidentCreated(c.isNotifyIncidentCreated())
                .notifyIncidentOpened(c.isNotifyIncidentOpened())
                .notifyIncidentClosed(c.isNotifyIncidentClosed())
                .notifyIncidentChatMessage(c.isNotifyIncidentChatMessage())
                .notificationRetentionDays(c.getNotificationRetentionDays())
                .notificationAutoCleanupEnabled(c.isNotificationAutoCleanupEnabled())
                .totalNotificationCount(notificationRepository.count())
                .build();
    }
}
