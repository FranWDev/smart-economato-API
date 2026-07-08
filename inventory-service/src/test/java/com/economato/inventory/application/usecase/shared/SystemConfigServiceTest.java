package com.economato.inventory.application.usecase.shared;

import com.economato.inventory.application.dto.stock.request.AlertsConfigRequestDTO;
import com.economato.inventory.application.dto.notification.request.NotificationsConfigRequestDTO;
import com.economato.inventory.application.dto.shared.request.PredictionsConfigRequestDTO;
import com.economato.inventory.application.dto.notification.request.PresenceConfigRequestDTO;
import com.economato.inventory.domain.model.notification.NotificationType;
import com.economato.inventory.domain.model.shared.SystemConfig;
import com.economato.inventory.domain.model.shared.SystemConfigAuditLog;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.notification.NotificationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared.SystemConfigAuditLogRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared.SystemConfigRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserActivityLogRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.scheduler.shared.DynamicSchedulerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private SystemConfigAuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserActivityLogRepository userActivityLogRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private I18nService i18nService;
    @Mock private DynamicSchedulerConfig dynamicSchedulerConfig;

    private SystemConfigService service;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(i18nService.getMessage(any(MessageKey.class))).thenAnswer(inv -> inv.getArgument(0, MessageKey.class).getKey());
        lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0, MessageKey.class).getKey());

        service = new SystemConfigService(
                systemConfigRepository,
                auditLogRepository,
                userRepository,
                userActivityLogRepository,
                notificationRepository,
                i18nService
        );
        setField(service, "dynamicSchedulerConfig", dynamicSchedulerConfig);
    }

    @Test
    void getConfig_WhenNoConfigExists_ShouldCreateDefault() {
        setUpSilently();
        when(systemConfigRepository.findById(1)).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        SystemConfig config = service.getConfigEntity();

        assertNotNull(config);
        assertEquals(1, config.getId());
        assertTrue(config.isPresenceAuditEnabled());
        assertEquals(21, config.getAlertThresholdOkDays());
        assertEquals(6, config.getPredictionRefreshIntervalHours());
        verify(systemConfigRepository).save(any(SystemConfig.class));
    }

    @Test
    void isNotificationTypeEnabled_ShouldReadFromConfigAndAlwaysAllowManual() {
        setUpSilently();
        SystemConfig cfg = SystemConfig.builder().id(1).notifyWeeklyPlanCreated(false).build();
        when(systemConfigRepository.findById(1)).thenReturn(Optional.of(cfg));

        assertFalse(service.isNotificationTypeEnabled(NotificationType.WEEKLY_PLAN_CREATED));
        assertTrue(service.isNotificationTypeEnabled(NotificationType.MANUAL));
    }

    @Test
    void updatePresenceConfig_ShouldUpdateAndAudit() {
        setUpSilently();
        SystemConfig cfg = SystemConfig.builder().id(1)
                .presenceAuditEnabled(true)
                .presenceAutoCleanupEnabled(false)
                .presenceAutoCleanupDays(null)
                .build();
        User admin = new User();
        admin.setId(99);
        admin.setName("admin");

        when(systemConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(userRepository.findByName("admin")).thenReturn(Optional.of(admin));
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        PresenceConfigRequestDTO request = PresenceConfigRequestDTO.builder()
                .presenceAuditEnabled(false)
                .presenceAutoCleanupEnabled(true)
                .presenceAutoCleanupDays(7)
                .build();

        SystemConfig updated = service.updatePresenceConfig(request, "admin");

        assertFalse(updated.isPresenceAuditEnabled());
        assertTrue(updated.isPresenceAutoCleanupEnabled());
        assertEquals(7, updated.getPresenceAutoCleanupDays());
        assertEquals(admin, updated.getUpdatedBy());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SystemConfigAuditLog>> captor = (ArgumentCaptor<List<SystemConfigAuditLog>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(auditLogRepository).saveAll(captor.capture());
        assertEquals(3, captor.getValue().size());
        assertEquals("PRESENCE", captor.getValue().get(0).getCategory());
    }

    @Test
    void updatePresenceConfig_WhenAutoCleanupEnabledWithoutDays_ShouldThrow() {
        setUpSilently();
        PresenceConfigRequestDTO request = PresenceConfigRequestDTO.builder()
                .presenceAuditEnabled(true)
                .presenceAutoCleanupEnabled(true)
                .presenceAutoCleanupDays(null)
                .build();

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> service.updatePresenceConfig(request, "admin"));
        assertNotNull(exception);
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    void updateAlertsConfig_WithValidThresholds_ShouldUpdate() {
        setUpSilently();
        SystemConfig cfg = SystemConfig.builder().id(1).build();
        when(systemConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(userRepository.findByName("admin")).thenReturn(Optional.of(new User()));
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertsConfigRequestDTO request = AlertsConfigRequestDTO.builder()
                .alertThresholdOkDays(30)
                .alertThresholdLowDays(20)
                .alertThresholdMediumDays(10)
                .alertThresholdHighDays(5)
                .expirationCriticalDays(2)
                .expirationHighDays(4)
                .expirationMediumDays(8)
                .forecastHorizonDays(14)
                .forecastHistoryWeeks(12)
                .build();

        SystemConfig updated = service.updateAlertsConfig(request, "admin");

        assertEquals(30, updated.getAlertThresholdOkDays());
        assertEquals(20, updated.getAlertThresholdLowDays());
        assertEquals(5, updated.getAlertThresholdHighDays());
        verify(auditLogRepository).saveAll(any());
    }

    @Test
    void updateAlertsConfig_WithInvalidThresholdOrder_ShouldThrow() {
        setUpSilently();
        AlertsConfigRequestDTO request = AlertsConfigRequestDTO.builder()
                .alertThresholdOkDays(5)
                .alertThresholdLowDays(10)
                .alertThresholdMediumDays(7)
                .alertThresholdHighDays(3)
                .expirationCriticalDays(2)
                .expirationHighDays(4)
                .expirationMediumDays(8)
                .forecastHorizonDays(14)
                .forecastHistoryWeeks(12)
                .build();

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> service.updateAlertsConfig(request, "admin"));
        assertNotNull(exception);
    }

    @Test
    void updateExpirationThresholds_WithInvalidOrder_ShouldThrow() {
        setUpSilently();
        AlertsConfigRequestDTO request = AlertsConfigRequestDTO.builder()
                .alertThresholdOkDays(30)
                .alertThresholdLowDays(20)
                .alertThresholdMediumDays(10)
                .alertThresholdHighDays(5)
                .expirationCriticalDays(20)
                .expirationHighDays(10)
                .expirationMediumDays(8)
                .forecastHorizonDays(14)
                .forecastHistoryWeeks(12)
                .build();

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> service.updateAlertsConfig(request, "admin"));
        assertNotNull(exception);
    }

    @Test
    void updatePredictionsConfig_WithInvalidInterval_ShouldThrow() {
        setUpSilently();
        PredictionsConfigRequestDTO request = PredictionsConfigRequestDTO.builder()
                .predictionRefreshEnabled(true)
                .predictionRefreshIntervalHours(0)
                .predictionHistoryDays(90)
                .predictionBatchSize(20)
                .build();

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> service.updatePredictionsConfig(request, "admin"));
        assertNotNull(exception);
    }

    @Test
    void updatePredictionsConfig_WhenIntervalChanges_ShouldRescheduleForecast() {
        setUpSilently();
        SystemConfig cfg = SystemConfig.builder().id(1)
                .predictionRefreshIntervalHours(6)
                .predictionHistoryDays(90)
                .predictionBatchSize(20)
                .build();
        when(systemConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(userRepository.findByName("admin")).thenReturn(Optional.of(new User()));
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        PredictionsConfigRequestDTO request = PredictionsConfigRequestDTO.builder()
                .predictionRefreshEnabled(true)
                .predictionRefreshIntervalHours(12)
                .predictionHistoryDays(120)
                .predictionBatchSize(40)
                .build();

        SystemConfig updated = service.updatePredictionsConfig(request, "admin");

        assertEquals(12, updated.getPredictionRefreshIntervalHours());
        verify(dynamicSchedulerConfig).reschedule("forecast");
    }

    @Test
    void updateNotificationsConfig_WhenRetentionEnabledWithoutDays_ShouldThrow() {
        setUpSilently();
        NotificationsConfigRequestDTO request = NotificationsConfigRequestDTO.builder()
                .notifyWeeklyPlanCreated(true)
                .notifyWeeklyPlanActivated(true)
                .notifyWeeklyPlanSlotConfirmed(true)
                .notifyWeeklyPlanDayConfirmed(true)
                .notifyWeeklyPlanCompleted(true)
                .notifyWeeklyPlanCancelled(true)
                .notifyFoodCrisisActivated(true)
                .notifyFoodCrisisLifted(true)
                .notifyStockPredictionTriggered(true)
                .notifyIncidentCreated(true)
                .notifyIncidentOpened(true)
                .notifyIncidentClosed(true)
                .notifyIncidentChatMessage(true)
                .notificationAutoCleanupEnabled(true)
                .notificationRetentionDays(null)
                .build();

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> service.updateNotificationsConfig(request, "admin"));
        assertNotNull(exception);
    }

    @Test
    void purgeLogs_WithoutDates_ShouldDeleteAll() {
        setUpSilently();
        when(userActivityLogRepository.count()).thenReturn(8L);

        int deleted = service.purgeActivityLogs(null, null);

        assertEquals(8, deleted);
        verify(userActivityLogRepository).deleteAll();
    }

    @Test
    void purgeLogs_WithDateRange_ShouldCallRepositoryWithRange() {
        setUpSilently();
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 31, 0, 0);
        when(userActivityLogRepository.deleteByTimestampBetween(from, to)).thenReturn(12);

        int deleted = service.purgeActivityLogs(from, to);

        assertEquals(12, deleted);
        verify(userActivityLogRepository).deleteByTimestampBetween(from, to);
    }

    @Test
    void purgeReadNotifications_WithDateRange_ShouldDeleteMatchingRows() {
        setUpSilently();
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 31, 0, 0);
        when(notificationRepository.deleteReadByCreatedAtBetween(from, to)).thenReturn(4);

        int deleted = service.purgeReadNotifications(from, to);

        assertEquals(4, deleted);
        verify(notificationRepository).deleteReadByCreatedAtBetween(from, to);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setUpSilently() {
        try {
            setUp();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
