package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.response.AdvancedConfigResponseDTO;
import com.economato.inventory.application.dto.response.AlertsConfigResponseDTO;
import com.economato.inventory.application.dto.response.IncidentsConfigResponseDTO;
import com.economato.inventory.application.dto.response.NotificationsConfigResponseDTO;
import com.economato.inventory.application.dto.response.PredictionsConfigResponseDTO;
import com.economato.inventory.application.dto.response.PresenceConfigResponseDTO;
import com.economato.inventory.application.dto.response.SecurityConfigResponseDTO;
import com.economato.inventory.application.dto.response.SessionsConfigResponseDTO;
import com.economato.inventory.application.dto.response.SystemConfigSnapshotResponseDTO;
import com.economato.inventory.application.usecase.SystemConfigService;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.NotificationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserActivityLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "Configuración del Sistema", description = "Snapshot de configuración para panel de administración")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;
    private final UserActivityLogRepository userActivityLogRepository;
    private final NotificationRepository notificationRepository;

    @GetMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener snapshot completo de configuración")
    public ResponseEntity<SystemConfigSnapshotResponseDTO> getCurrent() {
        var c = systemConfigService.getConfigEntity();
                var updatedByName = systemConfigService.getUpdatedByName();

        return ResponseEntity.ok(SystemConfigSnapshotResponseDTO.builder()
                .presence(PresenceConfigResponseDTO.builder()
                        .presenceAuditEnabled(c.isPresenceAuditEnabled())
                        .presenceAutoCleanupEnabled(c.isPresenceAutoCleanupEnabled())
                        .presenceAutoCleanupDays(c.getPresenceAutoCleanupDays())
                        .totalLogCount(userActivityLogRepository.count())
                        .build())
                .alerts(AlertsConfigResponseDTO.builder()
                        .alertThresholdOkDays(c.getAlertThresholdOkDays())
                        .alertThresholdLowDays(c.getAlertThresholdLowDays())
                        .alertThresholdMediumDays(c.getAlertThresholdMediumDays())
                        .alertThresholdHighDays(c.getAlertThresholdHighDays())
                        .expirationCriticalDays(c.getExpirationCriticalDays())
                        .expirationHighDays(c.getExpirationHighDays())
                        .expirationMediumDays(c.getExpirationMediumDays())
                        .forecastHorizonDays(c.getForecastHorizonDays())
                        .forecastHistoryWeeks(c.getForecastHistoryWeeks())
                        .build())
                .predictions(PredictionsConfigResponseDTO.builder()
                        .predictionRefreshEnabled(c.isPredictionRefreshEnabled())
                        .predictionRefreshIntervalHours(c.getPredictionRefreshIntervalHours())
                        .predictionHistoryDays(c.getPredictionHistoryDays())
                        .predictionBatchSize(c.getPredictionBatchSize())
                        .build())
                .sessions(SessionsConfigResponseDTO.builder()
                        .staleSessionTimeoutSeconds(c.getStaleSessionTimeoutSeconds())
                        .build())
                .security(SecurityConfigResponseDTO.builder()
                        .jwtExpirationMs(c.getJwtExpirationMs())
                        .minPasswordLength(c.getMinPasswordLength())
                        .maxEscalationMinutes(c.getMaxEscalationMinutes())
                        .build())
                .incidents(IncidentsConfigResponseDTO.builder()
                        .maxChatMessageLength(c.getMaxChatMessageLength())
                        .maxAdminAttachableAudits(c.getMaxAdminAttachableAudits())
                        .maxUploadFileSizeBytes(c.getMaxUploadFileSizeBytes())
                        .allowedFileTypes(c.getAllowedFileTypes())
                        .build())
                .notifications(NotificationsConfigResponseDTO.builder()
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
                        .build())
                .advanced(AdvancedConfigResponseDTO.builder()
                        .outboxProcessingIntervalMs(c.getOutboxProcessingIntervalMs())
                        .outboxBatchSize(c.getOutboxBatchSize())
                        .outboxMaxConsecutiveFailures(c.getOutboxMaxConsecutiveFailures())
                        .kafkaSendTimeoutSeconds(c.getKafkaSendTimeoutSeconds())
                        .build())
                                .updatedBy(updatedByName)
                .updatedAt(c.getUpdatedAt())
                .build());
    }
}
