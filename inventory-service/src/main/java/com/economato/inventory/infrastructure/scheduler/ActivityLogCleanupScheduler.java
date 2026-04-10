package com.economato.inventory.infrastructure.scheduler;

import com.economato.inventory.application.usecase.SystemConfigService;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserActivityLogRepository;
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
public class ActivityLogCleanupScheduler {

    private final SystemConfigService systemConfigService;
    private final UserActivityLogRepository userActivityLogRepository;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldLogs() {
        var cfg = systemConfigService.getConfigEntity();
        if (!cfg.isPresenceAutoCleanupEnabled() || cfg.getPresenceAutoCleanupDays() == null) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(cfg.getPresenceAutoCleanupDays());
        int deleted = userActivityLogRepository.deleteByTimestampBefore(threshold);
        log.info("Limpieza automática de logs de actividad completada. Eliminados: {}", deleted);
    }
}
