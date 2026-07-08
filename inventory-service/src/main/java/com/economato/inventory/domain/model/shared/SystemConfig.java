package com.economato.inventory.domain.model.shared;
import com.economato.inventory.domain.model.user.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "system_config")
@EntityListeners(AuditingEntityListener.class)
public class SystemConfig {

    @Id
    @Builder.Default
    @Column(name = "id", nullable = false)
    private Integer id = 1;

    // PRESENCE
    @Builder.Default
    @Column(name = "presence_audit_enabled", nullable = false)
    private boolean presenceAuditEnabled = true;

    @Builder.Default
    @Column(name = "presence_auto_cleanup_enabled", nullable = false)
    private boolean presenceAutoCleanupEnabled = false;

    @Column(name = "presence_auto_cleanup_days")
    private Integer presenceAutoCleanupDays;

    // ALERTS
    @Builder.Default
    @Column(name = "alert_threshold_ok_days", nullable = false)
    private int alertThresholdOkDays = 21;

    @Builder.Default
    @Column(name = "alert_threshold_low_days", nullable = false)
    private int alertThresholdLowDays = 14;

    @Builder.Default
    @Column(name = "alert_threshold_medium_days", nullable = false)
    private int alertThresholdMediumDays = 7;

    @Builder.Default
    @Column(name = "alert_threshold_high_days", nullable = false)
    private int alertThresholdHighDays = 3;

    @Builder.Default
    @Column(name = "expiration_critical_days", nullable = false)
    private int expirationCriticalDays = 3;

    @Builder.Default
    @Column(name = "expiration_high_days", nullable = false)
    private int expirationHighDays = 7;

    @Builder.Default
    @Column(name = "expiration_medium_days", nullable = false)
    private int expirationMediumDays = 14;

    @Builder.Default
    @Column(name = "forecast_horizon_days", nullable = false)
    private int forecastHorizonDays = 14;

    @Builder.Default
    @Column(name = "forecast_history_weeks", nullable = false)
    private int forecastHistoryWeeks = 12;

    // PREDICTIONS
    @Builder.Default
    @Column(name = "prediction_refresh_enabled", nullable = false)
    private boolean predictionRefreshEnabled = true;

    @Builder.Default
    @Column(name = "prediction_refresh_interval_hours", nullable = false)
    private int predictionRefreshIntervalHours = 6;

    @Builder.Default
    @Column(name = "prediction_history_days", nullable = false)
    private int predictionHistoryDays = 90;

    @Builder.Default
    @Column(name = "prediction_batch_size", nullable = false)
    private int predictionBatchSize = 20;

    // SESSIONS
    @Builder.Default
    @Column(name = "stale_session_timeout_seconds", nullable = false)
    private long staleSessionTimeoutSeconds = 60L;

    // SECURITY
    @Builder.Default
    @Column(name = "jwt_expiration_ms", nullable = false)
    private long jwtExpirationMs = 86_400_000L;

    @Builder.Default
    @Column(name = "min_password_length", nullable = false)
    private int minPasswordLength = 6;

    @Builder.Default
    @Column(name = "max_escalation_minutes", nullable = false)
    private int maxEscalationMinutes = 1_440;

    // INCIDENTS
    @Builder.Default
    @Column(name = "max_chat_message_length", nullable = false)
    private int maxChatMessageLength = 5_000;

    @Builder.Default
    @Column(name = "max_admin_attachable_audits", nullable = false)
    private int maxAdminAttachableAudits = 200;

    @Builder.Default
    @Column(name = "max_upload_file_size_bytes", nullable = false)
    private long maxUploadFileSizeBytes = 10_485_760L;

    @Builder.Default
    @Column(name = "allowed_file_types", nullable = false, length = 1000)
    private String allowedFileTypes = "image/jpeg,image/png,image/gif,image/webp,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // NOTIFICATIONS
    @Builder.Default
    @Column(name = "notify_weekly_plan_created", nullable = false)
    private boolean notifyWeeklyPlanCreated = true;
    @Builder.Default
    @Column(name = "notify_weekly_plan_activated", nullable = false)
    private boolean notifyWeeklyPlanActivated = true;
    @Builder.Default
    @Column(name = "notify_weekly_plan_slot_confirmed", nullable = false)
    private boolean notifyWeeklyPlanSlotConfirmed = true;
    @Builder.Default
    @Column(name = "notify_weekly_plan_day_confirmed", nullable = false)
    private boolean notifyWeeklyPlanDayConfirmed = true;
    @Builder.Default
    @Column(name = "notify_weekly_plan_completed", nullable = false)
    private boolean notifyWeeklyPlanCompleted = true;
    @Builder.Default
    @Column(name = "notify_weekly_plan_cancelled", nullable = false)
    private boolean notifyWeeklyPlanCancelled = true;
    @Builder.Default
    @Column(name = "notify_food_crisis_activated", nullable = false)
    private boolean notifyFoodCrisisActivated = true;
    @Builder.Default
    @Column(name = "notify_food_crisis_lifted", nullable = false)
    private boolean notifyFoodCrisisLifted = true;
    @Builder.Default
    @Column(name = "notify_stock_prediction_triggered", nullable = false)
    private boolean notifyStockPredictionTriggered = true;
    @Builder.Default
    @Column(name = "notify_incident_created", nullable = false)
    private boolean notifyIncidentCreated = true;
    @Builder.Default
    @Column(name = "notify_incident_opened", nullable = false)
    private boolean notifyIncidentOpened = true;
    @Builder.Default
    @Column(name = "notify_incident_closed", nullable = false)
    private boolean notifyIncidentClosed = true;
    @Builder.Default
    @Column(name = "notify_incident_chat_message", nullable = false)
    private boolean notifyIncidentChatMessage = true;

    @Column(name = "notification_retention_days")
    private Integer notificationRetentionDays;

    @Builder.Default
    @Column(name = "notification_auto_cleanup_enabled", nullable = false)
    private boolean notificationAutoCleanupEnabled = false;

    // ADVANCED
    @Builder.Default
    @Column(name = "outbox_processing_interval_ms", nullable = false)
    private long outboxProcessingIntervalMs = 5000L;

    @Builder.Default
    @Column(name = "outbox_batch_size", nullable = false)
    private int outboxBatchSize = 50;

    @Builder.Default
    @Column(name = "outbox_max_consecutive_failures", nullable = false)
    private int outboxMaxConsecutiveFailures = 3;

    @Builder.Default
    @Column(name = "kafka_send_timeout_seconds", nullable = false)
    private int kafkaSendTimeoutSeconds = 5;

    // METADATA
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @PrePersist
    public void ensureSingletonId() {
        if (id == null) {
            id = 1;
        }
    }
}
