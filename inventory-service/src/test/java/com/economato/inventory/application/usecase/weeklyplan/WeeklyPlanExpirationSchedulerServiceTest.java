package com.economato.inventory.application.usecase.weeklyplan;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;

import com.economato.inventory.domain.model.weeklyplan.WeeklyPlan;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlot;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlotStatus;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanStatus;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanExpirationSchedulerServiceTest {

    @Mock
    private WeeklyPlanRepository weeklyPlanRepository;

    @Mock
    private PersistentNotificationService persistentNotificationService;

    @InjectMocks
    private WeeklyPlanExpirationSchedulerService schedulerService;

    @Test
    void closeExpiredPlans_shouldMarkCompletedSilentlyWhenNoConfirmedSlots() {
        WeeklyPlan plan = createPlan(1L, WeeklyPlanStatus.ACTIVE,
                List.of(
                        createSlot(WeeklyPlanSlotStatus.PENDING),
                        createSlot(WeeklyPlanSlotStatus.IN_PROGRESS)
                ));

        when(weeklyPlanRepository.findExpiredActivePlans(any(LocalDate.class))).thenReturn(List.of(plan));

        schedulerService.closeExpiredPlans();

        assertEquals(WeeklyPlanStatus.COMPLETED, plan.getStatus());
        assertEquals(WeeklyPlanSlotStatus.PENDING, plan.getSlots().stream().filter(s -> s.getStatus() == WeeklyPlanSlotStatus.PENDING).findFirst().orElseThrow().getStatus());
        verify(weeklyPlanRepository, times(1)).save(plan);
        verify(persistentNotificationService, times(1)).notifyPlanAutoClosed(plan);
    }

    @Test
    void closeExpiredPlans_shouldMarkCompletedWhenHasConfirmedSlots() {
        WeeklyPlan plan = createPlan(2L, WeeklyPlanStatus.IN_PROGRESS,
                List.of(
                        createSlot(WeeklyPlanSlotStatus.CONFIRMED),
                        createSlot(WeeklyPlanSlotStatus.PENDING)
                ));

        when(weeklyPlanRepository.findExpiredActivePlans(any(LocalDate.class))).thenReturn(List.of(plan));

        schedulerService.closeExpiredPlans();

        assertEquals(WeeklyPlanStatus.COMPLETED, plan.getStatus());
        verify(weeklyPlanRepository, times(1)).save(plan);
        verify(persistentNotificationService, times(1)).notifyPlanAutoClosed(plan);
    }

    @Test
    void closeExpiredPlans_shouldSkipWhenNoneFound() {
        when(weeklyPlanRepository.findExpiredActivePlans(any(LocalDate.class))).thenReturn(Collections.emptyList());

        schedulerService.closeExpiredPlans();

        verify(weeklyPlanRepository, never()).save(any(WeeklyPlan.class));
        verify(persistentNotificationService, never()).notifyPlanAutoClosed(any(WeeklyPlan.class));
    }

    private WeeklyPlan createPlan(Long id, WeeklyPlanStatus status, List<WeeklyPlanSlot> slots) {
        WeeklyPlan plan = new WeeklyPlan();
        plan.setId(id);
        plan.setStatus(status);
        plan.setWeekStartDate(LocalDate.now().minusWeeks(1));
        plan.setWeekEndDate(LocalDate.now().minusDays(1));
        plan.setSlots(new java.util.HashSet<>(slots));

        for (WeeklyPlanSlot slot : slots) {
            slot.setWeeklyPlan(plan);
        }

        return plan;
    }

    private WeeklyPlanSlot createSlot(WeeklyPlanSlotStatus status) {
        WeeklyPlanSlot slot = new WeeklyPlanSlot();
        slot.setStatus(status);
        slot.setStudents(new java.util.HashSet<>());
        return slot;
    }
}
