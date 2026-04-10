package com.economato.inventory.infrastructure.scheduler;

import com.economato.inventory.application.usecase.SystemConfigService;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final SystemConfigService systemConfigService;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupReadNotifications() {
        var cfg = systemConfigService.getConfigEntity();
        if (!cfg.isNotificationAutoCleanupEnabled() || cfg.getNotificationRetentionDays() == null) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(cfg.getNotificationRetentionDays());
        int deleted = notificationRepository.deleteOldReadAndDeletedBefore(threshold);
        log.info("Limpieza automática de notificaciones completada. Eliminadas: {}", deleted);
    }
}
