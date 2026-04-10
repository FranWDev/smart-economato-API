package com.economato.inventory.infrastructure.scheduler;

import com.economato.inventory.application.usecase.ScheduledForecastRefreshService;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditOutboxProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Configuration
@Profile({ "!test", "kafka-test" })
@RequiredArgsConstructor
public class DynamicSchedulerConfig {

    private final ScheduledForecastRefreshService scheduledForecastRefreshService;
    private final AuditOutboxProcessor auditOutboxProcessor;

    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private ThreadPoolTaskScheduler dynamicTaskScheduler;

    @PostConstruct
    public void init() {
        this.dynamicTaskScheduler = new ThreadPoolTaskScheduler();
        this.dynamicTaskScheduler.setPoolSize(2);
        this.dynamicTaskScheduler.setThreadNamePrefix("dynamic-config-scheduler-");
        this.dynamicTaskScheduler.initialize();

        safeReschedule("forecast");
        safeReschedule("outbox");
    }

    private void safeReschedule(String taskName) {
        try {
            reschedule(taskName);
        } catch (Exception ex) {
            log.warn("No se pudo programar la tarea dinámica '{}': {}", taskName, ex.getMessage());
        }
    }

    public synchronized void reschedule(String taskName) {
        ScheduledFuture<?> existing = tasks.remove(taskName);
        if (existing != null) {
            existing.cancel(false);
        }

        if ("forecast".equals(taskName)) {
            long intervalMs = Math.max(1000L,
                Duration.ofHours(Math.max(1, scheduledForecastRefreshService.getRefreshIntervalHours())).toMillis());
            ScheduledFuture<?> future = dynamicTaskScheduler.scheduleWithFixedDelay(
                    scheduledForecastRefreshService::scheduleForecastRefresh,
                    Duration.ofMillis(intervalMs));
            tasks.put(taskName, future);
            log.info("Tarea forecast reprogramada cada {} ms", intervalMs);
            return;
        }

        if ("outbox".equals(taskName)) {
            long intervalMs = Math.max(1000L, auditOutboxProcessor.getOutboxIntervalMs());
            ScheduledFuture<?> future = dynamicTaskScheduler.scheduleWithFixedDelay(
                    auditOutboxProcessor::processOutbox,
                    Duration.ofMillis(intervalMs));
            tasks.put(taskName, future);
            log.info("Tarea outbox reprogramada cada {} ms", intervalMs);
        }
    }
}
