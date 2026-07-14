package com.economato.inventory.application.usecase.shared;
import com.economato.inventory.infrastructure.config.shared.PredictionConfig;
import com.economato.inventory.infrastructure.scheduler.shared.DynamicSchedulerConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.shared.request.AdvancedConfigRequestDTO;
import com.economato.inventory.application.dto.stock.request.AlertsConfigRequestDTO;
import com.economato.inventory.application.dto.incident.request.IncidentsConfigRequestDTO;
import com.economato.inventory.application.dto.notification.request.NotificationsConfigRequestDTO;
import com.economato.inventory.application.dto.notification.request.NotificationPurgeRequestDTO;
import com.economato.inventory.application.dto.shared.request.PredictionsConfigRequestDTO;
import com.economato.inventory.application.dto.notification.request.PresenceConfigRequestDTO;
import com.economato.inventory.application.dto.shared.request.ActivityLogPurgeRequestDTO;
import com.economato.inventory.application.dto.shared.request.SecurityConfigRequestDTO;
import com.economato.inventory.application.dto.shared.request.SessionsConfigRequestDTO;
import com.economato.inventory.application.dto.shared.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.notification.response.NotificationsConfigResponseDTO;
import com.economato.inventory.application.dto.notification.response.PresenceConfigResponseDTO;
import com.economato.inventory.application.dto.shared.response.PurgeResultResponseDTO;
import com.economato.inventory.domain.model.notification.NotificationType;
import com.economato.inventory.domain.model.shared.SystemConfig;
import com.economato.inventory.domain.model.shared.SystemConfigAuditLog;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.notification.NotificationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared.SystemConfigAuditLogRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared.SystemConfigRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserActivityLogRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final String CATEGORY_PRESENCE = "PRESENCE";
    private static final String CATEGORY_ALERTS = "ALERTS";
    private static final String CATEGORY_PREDICTIONS = "PREDICTIONS";
    private static final String CATEGORY_SESSIONS = "SESSIONS";
    private static final String CATEGORY_SECURITY = "SECURITY";
    private static final String CATEGORY_INCIDENTS = "INCIDENTS";
    private static final String CATEGORY_NOTIFICATIONS = "NOTIFICATIONS";
    private static final String CATEGORY_ADVANCED = "ADVANCED";

    private final SystemConfigRepository systemConfigRepository;
    private final SystemConfigAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final UserActivityLogRepository userActivityLogRepository;
    private final NotificationRepository notificationRepository;
    private final I18nService i18nService;
    private final ObjectProvider<DynamicSchedulerConfig> dynamicSchedulerConfigProvider;

    @Cacheable("system_config")
    @Transactional(readOnly = true)
    public SystemConfig getConfigEntity() {
        return systemConfigRepository.findById(1)
                .orElseGet(() -> systemConfigRepository.save(SystemConfig.builder().id(1).build()));
    }

    @Transactional(readOnly = true)
    public boolean isPresenceAuditEnabled() {
        return getConfigEntity().isPresenceAuditEnabled();
    }

    @Transactional(readOnly = true)
    public boolean isNotificationTypeEnabled(NotificationType type) {
        if (type == null || type == NotificationType.MANUAL) {
            return true;
        }
        SystemConfig cfg = getConfigEntity();
        return switch (type) {
            case WEEKLY_PLAN_CREATED -> cfg.isNotifyWeeklyPlanCreated();
            case WEEKLY_PLAN_ACTIVATED -> cfg.isNotifyWeeklyPlanActivated();
            case WEEKLY_PLAN_SLOT_CONFIRMED -> cfg.isNotifyWeeklyPlanSlotConfirmed();
            case WEEKLY_PLAN_DAY_CONFIRMED -> cfg.isNotifyWeeklyPlanDayConfirmed();
            case WEEKLY_PLAN_COMPLETED -> cfg.isNotifyWeeklyPlanCompleted();
            case WEEKLY_PLAN_CANCELLED -> cfg.isNotifyWeeklyPlanCancelled();
            case WEEKLY_PLAN_AUTO_CLOSED -> true;
            case FOOD_CRISIS_ACTIVATED -> cfg.isNotifyFoodCrisisActivated();
            case FOOD_CRISIS_LIFTED -> cfg.isNotifyFoodCrisisLifted();
            case STOCK_PREDICTION_TRIGGERED -> cfg.isNotifyStockPredictionTriggered();
            case INCIDENT_CREATED -> cfg.isNotifyIncidentCreated();
            case INCIDENT_OPENED -> cfg.isNotifyIncidentOpened();
            case INCIDENT_CLOSED -> cfg.isNotifyIncidentClosed();
            case INCIDENT_CHAT_MESSAGE -> cfg.isNotifyIncidentChatMessage();
            case DRAFT_SUBMITTED, DRAFT_APPROVED, DRAFT_REJECTED, DRAFT_RESUBMITTED -> true;
            case MANUAL -> true;
        };
    }

    @Transactional(readOnly = true)
    public long getStaleSessionTimeoutSeconds() { return getConfigEntity().getStaleSessionTimeoutSeconds(); }
    @Transactional(readOnly = true)
    public long getJwtExpirationMs() { return getConfigEntity().getJwtExpirationMs(); }
    @Transactional(readOnly = true)
    public String getUpdatedByName() {
        return systemConfigRepository.findById(1)
                .map(SystemConfig::getUpdatedBy)
                .map(User::getName)
                .orElse(null);
    }
    @Transactional(readOnly = true)
    public int getMinPasswordLength() { return getConfigEntity().getMinPasswordLength(); }
    @Transactional(readOnly = true)
    public int getMaxEscalationMinutes() { return getConfigEntity().getMaxEscalationMinutes(); }
    @Transactional(readOnly = true)
    public int getMaxChatMessageLength() { return getConfigEntity().getMaxChatMessageLength(); }
    @Transactional(readOnly = true)
    public int getMaxAdminAttachableAudits() { return getConfigEntity().getMaxAdminAttachableAudits(); }
    @Transactional(readOnly = true)
    public long getMaxUploadFileSizeBytes() { return getConfigEntity().getMaxUploadFileSizeBytes(); }

    @Transactional(readOnly = true)
    public Set<String> getAllowedFileTypes() {
        String raw = getConfigEntity().getAllowedFileTypes();
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String v = token == null ? null : token.trim();
            if (v != null && !v.isBlank()) {
                result.add(v);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AlertThresholds getAlertThresholds() {
        SystemConfig c = getConfigEntity();
        return new AlertThresholds(
                c.getAlertThresholdOkDays(),
                c.getAlertThresholdLowDays(),
                c.getAlertThresholdMediumDays(),
                c.getAlertThresholdHighDays(),
                c.getExpirationCriticalDays(),
                c.getExpirationHighDays(),
                c.getExpirationMediumDays());
    }

    @Transactional(readOnly = true)
    public PredictionConfig getPredictionConfig() {
        SystemConfig c = getConfigEntity();
        return new PredictionConfig(
                c.isPredictionRefreshEnabled(),
                c.getPredictionRefreshIntervalHours(),
                c.getPredictionHistoryDays(),
                c.getPredictionBatchSize());
    }

    @Transactional(readOnly = true)
    public OutboxConfig getOutboxConfig() {
        SystemConfig c = getConfigEntity();
        return new OutboxConfig(
                c.getOutboxProcessingIntervalMs(),
                c.getOutboxBatchSize(),
                c.getOutboxMaxConsecutiveFailures(),
                c.getKafkaSendTimeoutSeconds());
    }

    @Transactional
    @CacheEvict(value = "system_config", allEntries = true)
    public SystemConfig updatePresenceConfig(PresenceConfigRequestDTO dto, String adminUsername) {
        if (Boolean.TRUE.equals(dto.getPresenceAutoCleanupEnabled())
                && (dto.getPresenceAutoCleanupDays() == null || dto.getPresenceAutoCleanupDays() < 1)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_CONFIG_CLEANUP_DAYS_REQUIRED));
        }
        SystemConfig cfg = getConfigEntity();
        User admin = resolveAdmin(adminUsername);
        List<SystemConfigAuditLog> logs = new ArrayList<>();

        setIfChanged(cfg.isPresenceAuditEnabled(), dto.getPresenceAuditEnabled(), v -> cfg.setPresenceAuditEnabled(v), logs, admin, CATEGORY_PRESENCE, "presenceAuditEnabled");
        setIfChanged(cfg.isPresenceAutoCleanupEnabled(), dto.getPresenceAutoCleanupEnabled(), v -> cfg.setPresenceAutoCleanupEnabled(v), logs, admin, CATEGORY_PRESENCE, "presenceAutoCleanupEnabled");
        setIfChanged(cfg.getPresenceAutoCleanupDays(), dto.getPresenceAutoCleanupDays(), cfg::setPresenceAutoCleanupDays, logs, admin, CATEGORY_PRESENCE, "presenceAutoCleanupDays");

        cfg.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(cfg);
        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "system_config", allEntries = true)
    @RealtimeSync(entityType = "config", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"stock_alerts"})
    public SystemConfig updateAlertsConfig(AlertsConfigRequestDTO dto, String adminUsername) {
        if (!(dto.getAlertThresholdOkDays() > dto.getAlertThresholdLowDays()
                && dto.getAlertThresholdLowDays() > dto.getAlertThresholdMediumDays()
                && dto.getAlertThresholdMediumDays() > dto.getAlertThresholdHighDays()
                && dto.getAlertThresholdHighDays() > 0)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_CONFIG_ALERT_THRESHOLDS_INVALID));
        }
        if (!(dto.getExpirationCriticalDays() < dto.getExpirationHighDays()
                && dto.getExpirationHighDays() < dto.getExpirationMediumDays())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_CONFIG_EXPIRATION_THRESHOLDS_INVALID));
        }

        SystemConfig cfg = getConfigEntity();
        User admin = resolveAdmin(adminUsername);
        List<SystemConfigAuditLog> logs = new ArrayList<>();
        setIfChanged(cfg.getAlertThresholdOkDays(), dto.getAlertThresholdOkDays(), cfg::setAlertThresholdOkDays, logs, admin, CATEGORY_ALERTS, "alertThresholdOkDays");
        setIfChanged(cfg.getAlertThresholdLowDays(), dto.getAlertThresholdLowDays(), cfg::setAlertThresholdLowDays, logs, admin, CATEGORY_ALERTS, "alertThresholdLowDays");
        setIfChanged(cfg.getAlertThresholdMediumDays(), dto.getAlertThresholdMediumDays(), cfg::setAlertThresholdMediumDays, logs, admin, CATEGORY_ALERTS, "alertThresholdMediumDays");
        setIfChanged(cfg.getAlertThresholdHighDays(), dto.getAlertThresholdHighDays(), cfg::setAlertThresholdHighDays, logs, admin, CATEGORY_ALERTS, "alertThresholdHighDays");
        setIfChanged(cfg.getExpirationCriticalDays(), dto.getExpirationCriticalDays(), cfg::setExpirationCriticalDays, logs, admin, CATEGORY_ALERTS, "expirationCriticalDays");
        setIfChanged(cfg.getExpirationHighDays(), dto.getExpirationHighDays(), cfg::setExpirationHighDays, logs, admin, CATEGORY_ALERTS, "expirationHighDays");
        setIfChanged(cfg.getExpirationMediumDays(), dto.getExpirationMediumDays(), cfg::setExpirationMediumDays, logs, admin, CATEGORY_ALERTS, "expirationMediumDays");
        setIfChanged(cfg.getForecastHorizonDays(), dto.getForecastHorizonDays(), cfg::setForecastHorizonDays, logs, admin, CATEGORY_ALERTS, "forecastHorizonDays");
        setIfChanged(cfg.getForecastHistoryWeeks(), dto.getForecastHistoryWeeks(), cfg::setForecastHistoryWeeks, logs, admin, CATEGORY_ALERTS, "forecastHistoryWeeks");
        cfg.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(cfg);
        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "system_config", allEntries = true)
    @RealtimeSync(entityType = "config", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"stock_alerts"})
    public SystemConfig updatePredictionsConfig(PredictionsConfigRequestDTO dto, String adminUsername) {
        if (dto.getPredictionRefreshIntervalHours() < 1 || dto.getPredictionHistoryDays() < 7 || dto.getPredictionBatchSize() < 1) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_CONFIG_PREDICTION_INTERVAL_INVALID));
        }
        SystemConfig cfg = getConfigEntity();
        int oldInterval = cfg.getPredictionRefreshIntervalHours();
        User admin = resolveAdmin(adminUsername);
        List<SystemConfigAuditLog> logs = new ArrayList<>();
        setIfChanged(cfg.isPredictionRefreshEnabled(), dto.getPredictionRefreshEnabled(), cfg::setPredictionRefreshEnabled, logs, admin, CATEGORY_PREDICTIONS, "predictionRefreshEnabled");
        setIfChanged(cfg.getPredictionRefreshIntervalHours(), dto.getPredictionRefreshIntervalHours(), cfg::setPredictionRefreshIntervalHours, logs, admin, CATEGORY_PREDICTIONS, "predictionRefreshIntervalHours");
        setIfChanged(cfg.getPredictionHistoryDays(), dto.getPredictionHistoryDays(), cfg::setPredictionHistoryDays, logs, admin, CATEGORY_PREDICTIONS, "predictionHistoryDays");
        setIfChanged(cfg.getPredictionBatchSize(), dto.getPredictionBatchSize(), cfg::setPredictionBatchSize, logs, admin, CATEGORY_PREDICTIONS, "predictionBatchSize");
        cfg.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(cfg);
        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
        DynamicSchedulerConfig scheduler = dynamicSchedulerConfigProvider.getIfAvailable();
        if (scheduler != null && oldInterval != dto.getPredictionRefreshIntervalHours()) {
            scheduler.reschedule("forecast");
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "system_config", allEntries = true)
    public SystemConfig updateSessionsConfig(SessionsConfigRequestDTO dto, String adminUsername) {
        SystemConfig cfg = getConfigEntity();
        User admin = resolveAdmin(adminUsername);
        List<SystemConfigAuditLog> logs = new ArrayList<>();
        setIfChanged(cfg.getStaleSessionTimeoutSeconds(), dto.getStaleSessionTimeoutSeconds(), cfg::setStaleSessionTimeoutSeconds, logs, admin, CATEGORY_SESSIONS, "staleSessionTimeoutSeconds");
        cfg.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(cfg);
        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "system_config", allEntries = true)
    public SystemConfig updateSecurityConfig(SecurityConfigRequestDTO dto, String adminUsername) {
        SystemConfig cfg = getConfigEntity();
        User admin = resolveAdmin(adminUsername);
        List<SystemConfigAuditLog> logs = new ArrayList<>();
        setIfChanged(cfg.getJwtExpirationMs(), dto.getJwtExpirationMs(), cfg::setJwtExpirationMs, logs, admin, CATEGORY_SECURITY, "jwtExpirationMs");
        setIfChanged(cfg.getMinPasswordLength(), dto.getMinPasswordLength(), cfg::setMinPasswordLength, logs, admin, CATEGORY_SECURITY, "minPasswordLength");
        setIfChanged(cfg.getMaxEscalationMinutes(), dto.getMaxEscalationMinutes(), cfg::setMaxEscalationMinutes, logs, admin, CATEGORY_SECURITY, "maxEscalationMinutes");
        cfg.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(cfg);
        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "system_config", allEntries = true)
    public SystemConfig updateIncidentsConfig(IncidentsConfigRequestDTO dto, String adminUsername) {
        SystemConfig cfg = getConfigEntity();
        User admin = resolveAdmin(adminUsername);
        List<SystemConfigAuditLog> logs = new ArrayList<>();
        setIfChanged(cfg.getMaxChatMessageLength(), dto.getMaxChatMessageLength(), cfg::setMaxChatMessageLength, logs, admin, CATEGORY_INCIDENTS, "maxChatMessageLength");
        setIfChanged(cfg.getMaxAdminAttachableAudits(), dto.getMaxAdminAttachableAudits(), cfg::setMaxAdminAttachableAudits, logs, admin, CATEGORY_INCIDENTS, "maxAdminAttachableAudits");
        setIfChanged(cfg.getMaxUploadFileSizeBytes(), dto.getMaxUploadFileSizeBytes(), cfg::setMaxUploadFileSizeBytes, logs, admin, CATEGORY_INCIDENTS, "maxUploadFileSizeBytes");
        setIfChanged(cfg.getAllowedFileTypes(), dto.getAllowedFileTypes(), cfg::setAllowedFileTypes, logs, admin, CATEGORY_INCIDENTS, "allowedFileTypes");
        cfg.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(cfg);
        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "system_config", allEntries = true)
    public SystemConfig updateNotificationsConfig(NotificationsConfigRequestDTO dto, String adminUsername) {
        if (Boolean.TRUE.equals(dto.getNotificationAutoCleanupEnabled())
                && (dto.getNotificationRetentionDays() == null || dto.getNotificationRetentionDays() < 1)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_CONFIG_NOTIFICATION_RETENTION_DAYS_REQUIRED));
        }

        SystemConfig cfg = getConfigEntity();
        User admin = resolveAdmin(adminUsername);
        List<SystemConfigAuditLog> logs = new ArrayList<>();
        setIfChanged(cfg.isNotifyWeeklyPlanCreated(), dto.getNotifyWeeklyPlanCreated(), cfg::setNotifyWeeklyPlanCreated, logs, admin, CATEGORY_NOTIFICATIONS, "notifyWeeklyPlanCreated");
        setIfChanged(cfg.isNotifyWeeklyPlanActivated(), dto.getNotifyWeeklyPlanActivated(), cfg::setNotifyWeeklyPlanActivated, logs, admin, CATEGORY_NOTIFICATIONS, "notifyWeeklyPlanActivated");
        setIfChanged(cfg.isNotifyWeeklyPlanSlotConfirmed(), dto.getNotifyWeeklyPlanSlotConfirmed(), cfg::setNotifyWeeklyPlanSlotConfirmed, logs, admin, CATEGORY_NOTIFICATIONS, "notifyWeeklyPlanSlotConfirmed");
        setIfChanged(cfg.isNotifyWeeklyPlanDayConfirmed(), dto.getNotifyWeeklyPlanDayConfirmed(), cfg::setNotifyWeeklyPlanDayConfirmed, logs, admin, CATEGORY_NOTIFICATIONS, "notifyWeeklyPlanDayConfirmed");
        setIfChanged(cfg.isNotifyWeeklyPlanCompleted(), dto.getNotifyWeeklyPlanCompleted(), cfg::setNotifyWeeklyPlanCompleted, logs, admin, CATEGORY_NOTIFICATIONS, "notifyWeeklyPlanCompleted");
        setIfChanged(cfg.isNotifyWeeklyPlanCancelled(), dto.getNotifyWeeklyPlanCancelled(), cfg::setNotifyWeeklyPlanCancelled, logs, admin, CATEGORY_NOTIFICATIONS, "notifyWeeklyPlanCancelled");
        setIfChanged(cfg.isNotifyFoodCrisisActivated(), dto.getNotifyFoodCrisisActivated(), cfg::setNotifyFoodCrisisActivated, logs, admin, CATEGORY_NOTIFICATIONS, "notifyFoodCrisisActivated");
        setIfChanged(cfg.isNotifyFoodCrisisLifted(), dto.getNotifyFoodCrisisLifted(), cfg::setNotifyFoodCrisisLifted, logs, admin, CATEGORY_NOTIFICATIONS, "notifyFoodCrisisLifted");
        setIfChanged(cfg.isNotifyStockPredictionTriggered(), dto.getNotifyStockPredictionTriggered(), cfg::setNotifyStockPredictionTriggered, logs, admin, CATEGORY_NOTIFICATIONS, "notifyStockPredictionTriggered");
        setIfChanged(cfg.isNotifyIncidentCreated(), dto.getNotifyIncidentCreated(), cfg::setNotifyIncidentCreated, logs, admin, CATEGORY_NOTIFICATIONS, "notifyIncidentCreated");
        setIfChanged(cfg.isNotifyIncidentOpened(), dto.getNotifyIncidentOpened(), cfg::setNotifyIncidentOpened, logs, admin, CATEGORY_NOTIFICATIONS, "notifyIncidentOpened");
        setIfChanged(cfg.isNotifyIncidentClosed(), dto.getNotifyIncidentClosed(), cfg::setNotifyIncidentClosed, logs, admin, CATEGORY_NOTIFICATIONS, "notifyIncidentClosed");
        setIfChanged(cfg.isNotifyIncidentChatMessage(), dto.getNotifyIncidentChatMessage(), cfg::setNotifyIncidentChatMessage, logs, admin, CATEGORY_NOTIFICATIONS, "notifyIncidentChatMessage");
        setIfChanged(cfg.isNotificationAutoCleanupEnabled(), dto.getNotificationAutoCleanupEnabled(), cfg::setNotificationAutoCleanupEnabled, logs, admin, CATEGORY_NOTIFICATIONS, "notificationAutoCleanupEnabled");
        setIfChanged(cfg.getNotificationRetentionDays(), dto.getNotificationRetentionDays(), cfg::setNotificationRetentionDays, logs, admin, CATEGORY_NOTIFICATIONS, "notificationRetentionDays");

        cfg.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(cfg);
        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "system_config", allEntries = true)
    public SystemConfig updateAdvancedConfig(AdvancedConfigRequestDTO dto, String adminUsername) {
        SystemConfig cfg = getConfigEntity();
        long oldInterval = cfg.getOutboxProcessingIntervalMs();
        User admin = resolveAdmin(adminUsername);
        List<SystemConfigAuditLog> logs = new ArrayList<>();
        setIfChanged(cfg.getOutboxProcessingIntervalMs(), dto.getOutboxProcessingIntervalMs(), cfg::setOutboxProcessingIntervalMs, logs, admin, CATEGORY_ADVANCED, "outboxProcessingIntervalMs");
        setIfChanged(cfg.getOutboxBatchSize(), dto.getOutboxBatchSize(), cfg::setOutboxBatchSize, logs, admin, CATEGORY_ADVANCED, "outboxBatchSize");
        setIfChanged(cfg.getOutboxMaxConsecutiveFailures(), dto.getOutboxMaxConsecutiveFailures(), cfg::setOutboxMaxConsecutiveFailures, logs, admin, CATEGORY_ADVANCED, "outboxMaxConsecutiveFailures");
        setIfChanged(cfg.getKafkaSendTimeoutSeconds(), dto.getKafkaSendTimeoutSeconds(), cfg::setKafkaSendTimeoutSeconds, logs, admin, CATEGORY_ADVANCED, "kafkaSendTimeoutSeconds");
        cfg.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(cfg);
        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
        DynamicSchedulerConfig scheduler = dynamicSchedulerConfigProvider.getIfAvailable();
        if (scheduler != null && oldInterval != dto.getOutboxProcessingIntervalMs()) {
            scheduler.reschedule("outbox");
        }
        return saved;
    }

    @Transactional
    public int purgeActivityLogs(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            int count = Math.toIntExact(userActivityLogRepository.count());
            userActivityLogRepository.deleteAll();
            return count;
        }
        if (from == null) {
            return userActivityLogRepository.deleteByTimestampBefore(to);
        }
        if (to == null) {
            return userActivityLogRepository.deleteByTimestampBetween(from, LocalDateTime.now());
        }
        return userActivityLogRepository.deleteByTimestampBetween(from, to);
    }

    @Transactional
    public int purgeReadNotifications(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return (int) notificationRepository.deleteByIsReadTrue();
        }
        if (from == null) {
            return notificationRepository.deleteReadByCreatedAtBefore(to);
        }
        return notificationRepository.deleteReadByCreatedAtBetween(from, to != null ? to : LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SystemConfigAuditLog> getAuditByCategory(String category, org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.findByCategoryOrderByChangedAtDesc(category, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SystemConfigAuditLog> getAuditGlobal(org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.findAllByOrderByChangedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ConfigAuditLogResponseDTO> getAuditByCategoryDto(String category, org.springframework.data.domain.Pageable pageable) {
        return getAuditByCategory(category, pageable).map(this::toAuditDto);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ConfigAuditLogResponseDTO> getAuditGlobalDto(org.springframework.data.domain.Pageable pageable) {
        return getAuditGlobal(pageable).map(this::toAuditDto);
    }

    private ConfigAuditLogResponseDTO toAuditDto(SystemConfigAuditLog log) {
        return ConfigAuditLogResponseDTO.builder()
                .username(log.getUser() != null ? log.getUser().getName() : null)
                .category(log.getCategory())
                .fieldName(log.getFieldName())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .changedAt(log.getChangedAt())
                .build();
    }

    private User resolveAdmin(String adminUsername) {
        return userRepository.findByName(adminUsername).orElse(null);
    }

    private <T> void setIfChanged(T oldValue,
                                  T newValue,
                                  java.util.function.Consumer<T> setter,
                                  List<SystemConfigAuditLog> logs,
                                  User admin,
                                  String category,
                                  String fieldName) {
        if (!Objects.equals(oldValue, newValue)) {
            setter.accept(newValue);
            logs.add(SystemConfigAuditLog.builder()
                    .user(admin)
                    .category(category)
                    .fieldName(fieldName)
                    .oldValue(oldValue == null ? null : String.valueOf(oldValue))
                    .newValue(newValue == null ? null : String.valueOf(newValue))
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public NotificationsConfigResponseDTO getNotificationsConfigDto() {
        SystemConfig c = getConfigEntity();
        return toNotificationsConfigResponseDto(c);
    }

    @Transactional
    public NotificationsConfigResponseDTO updateNotificationsConfigDto(NotificationsConfigRequestDTO request, String adminUsername) {
        SystemConfig c = updateNotificationsConfig(request, adminUsername);
        return toNotificationsConfigResponseDto(c);
    }

    private NotificationsConfigResponseDTO toNotificationsConfigResponseDto(SystemConfig c) {
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

    @Transactional
    public PurgeResultResponseDTO purgeReadNotificationsDto(NotificationPurgeRequestDTO request) {
        LocalDateTime from = request == null ? null : request.getFrom();
        LocalDateTime to = request == null ? null : request.getTo();
        int deleted = purgeReadNotifications(from, to);
        return PurgeResultResponseDTO.builder().deletedCount(deleted).build();
    }

    @Transactional(readOnly = true)
    public PresenceConfigResponseDTO getPresenceConfigDto() {
        SystemConfig c = getConfigEntity();
        return toPresenceConfigResponseDto(c);
    }

    @Transactional
    public PresenceConfigResponseDTO updatePresenceConfigDto(PresenceConfigRequestDTO request, String adminUsername) {
        SystemConfig c = updatePresenceConfig(request, adminUsername);
        return toPresenceConfigResponseDto(c);
    }

    private PresenceConfigResponseDTO toPresenceConfigResponseDto(SystemConfig c) {
        return PresenceConfigResponseDTO.builder()
                .presenceAuditEnabled(c.isPresenceAuditEnabled())
                .presenceAutoCleanupEnabled(c.isPresenceAutoCleanupEnabled())
                .presenceAutoCleanupDays(c.getPresenceAutoCleanupDays())
                .totalLogCount(userActivityLogRepository.count())
                .build();
    }

    @Transactional
    public PurgeResultResponseDTO purgeActivityLogsDto(ActivityLogPurgeRequestDTO request) {
        LocalDateTime from = request == null ? null : request.getFrom();
        LocalDateTime to = request == null ? null : request.getTo();
        int deleted = purgeActivityLogs(from, to);
        return PurgeResultResponseDTO.builder().deletedCount(deleted).build();
    }

    public record AlertThresholds(int alertThresholdOkDays,
                                  int alertThresholdLowDays,
                                  int alertThresholdMediumDays,
                                  int alertThresholdHighDays,
                                  int expirationCriticalDays,
                                  int expirationHighDays,
                                  int expirationMediumDays) {
    }

    public record PredictionConfig(boolean predictionRefreshEnabled,
                                   int predictionRefreshIntervalHours,
                                   int predictionHistoryDays,
                                   int predictionBatchSize) {
    }

    public record OutboxConfig(long outboxProcessingIntervalMs,
                               int outboxBatchSize,
                               int outboxMaxConsecutiveFailures,
                               int kafkaSendTimeoutSeconds) {
    }
}