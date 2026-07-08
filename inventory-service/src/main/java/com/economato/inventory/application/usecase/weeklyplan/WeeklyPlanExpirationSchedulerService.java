package com.economato.inventory.application.usecase.weeklyplan;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;

import com.economato.inventory.domain.model.weeklyplan.WeeklyPlan;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanStatus;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile({"!test", "kafka-test"})
public class WeeklyPlanExpirationSchedulerService {

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final PersistentNotificationService persistentNotificationService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    @CacheEvict(value = {"weekly_plan", "weekly_plan_requirements", "student_metrics"}, allEntries = true)
    public void closeExpiredPlansOnStartup() {
        log.info("Checking for expired weekly plans on startup...");
        closeExpiredPlans();
    }

    void closeExpiredPlans() {
        LocalDate today = LocalDate.now();
        List<WeeklyPlan> expiredPlans = weeklyPlanRepository.findExpiredActivePlans(today);

        if (expiredPlans.isEmpty()) {
            log.info("No expired weekly plans found.");
            return;
        }

        for (WeeklyPlan plan : expiredPlans) {
            // Silent closure: release virtual reservation by moving the plan out of ACTIVE/IN_PROGRESS.
            // Keep slot statuses untouched to avoid surfacing explicit cancellations.
            WeeklyPlanStatus newStatus = WeeklyPlanStatus.COMPLETED;
            plan.setStatus(newStatus);

            weeklyPlanRepository.save(plan);

            try {
                persistentNotificationService.notifyPlanAutoClosed(plan);
            } catch (Exception e) {
                log.warn("Failed to send auto-close notification for plan #{}: {}", plan.getId(), e.getMessage());
            }

            log.info("Weekly plan #{} (chef={}, week={}) auto-closed with status {}",
                    plan.getId(),
                    plan.getChef() != null ? plan.getChef().getName() : "N/A",
                    plan.getWeekStartDate(),
                    newStatus);
        }

        log.info("Auto-closed {} expired weekly plan(s).", expiredPlans.size());
    }
}
