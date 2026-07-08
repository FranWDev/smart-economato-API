package com.economato.inventory.application.usecase.crisis;

import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.RoleNotificationMessage;
import com.economato.inventory.application.usecase.notification.RoleNotificationService;
import com.economato.inventory.application.usecase.stock.AlertCode;
import com.economato.inventory.domain.model.notification.NotificationType;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlan;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlot;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlotStatus;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanStatus;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceabilityNotificationService {

    private final RoleNotificationService notificationService;
    private final PersistentNotificationService persistentNotificationService;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final UserRepository userRepository;
    private final I18nService i18nService;

    public void broadcastCrisisNotification(String title, String body, AlertCode code, Long crisisId) {
        RoleNotificationMessage message = RoleNotificationMessage.builder()
                .title(title)
                .message(body)
                .code(code)
                .timestamp(LocalDateTime.now())
                .build();

        for (Role role : Role.values()) {
            notificationService.sendNotificationToRole(role, message);
        }

        persistentNotificationService.notifyCrisis(title, body, code, crisisId);
    }

    public void cancelAffectedWeeklyPlans(List<Integer> affectedProductIds, String crisisCode) {
        if (weeklyPlanRepository == null || userRepository == null) {
            return;
        }

        List<WeeklyPlan> activePlans = weeklyPlanRepository.findActivePlansWithPendingSlots();
        for (WeeklyPlan plan : activePlans) {
            boolean affected = false;

            for (WeeklyPlanSlot slot : plan.getSlots()) {
                if (slot.getStatus() != WeeklyPlanSlotStatus.PENDING
                        && slot.getStatus() != WeeklyPlanSlotStatus.IN_PROGRESS) {
                    continue;
                }

                boolean slotAffected = slot.getRecipe().getComponents().stream()
                        .anyMatch(rc -> affectedProductIds.contains(rc.getProduct().getId()));
                if (slotAffected) {
                    slot.setStatus(WeeklyPlanSlotStatus.CANCELLED);
                    affected = true;
                }
            }

            if (!affected) {
                continue;
            }

            boolean hasConfirmedSlots = plan.getSlots().stream()
                    .anyMatch(slot -> slot.getStatus() == WeeklyPlanSlotStatus.CONFIRMED);
            boolean hasOpenSlots = plan.getSlots().stream()
                    .anyMatch(slot -> slot.getStatus() == WeeklyPlanSlotStatus.PENDING
                            || slot.getStatus() == WeeklyPlanSlotStatus.IN_PROGRESS);

            if (hasConfirmedSlots && hasOpenSlots) {
                plan.setStatus(WeeklyPlanStatus.IN_PROGRESS);
            } else if (hasConfirmedSlots) {
                plan.setStatus(WeeklyPlanStatus.COMPLETED);
            } else if (hasOpenSlots) {
                plan.setStatus(WeeklyPlanStatus.ACTIVE);
            } else {
                plan.setStatus(WeeklyPlanStatus.CANCELLED);
            }

            weeklyPlanRepository.save(plan);

            List<User> recipients = new ArrayList<>();
            recipients.addAll(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN));
            if (plan.getChef() != null) {
                recipients.add(plan.getChef());
            }

            List<User> uniqueRecipients = recipients.stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left,
                            java.util.LinkedHashMap::new))
                    .values().stream().toList();

            String chefName = plan.getChef() != null ? plan.getChef().getName() : "N/A";
            String title = i18nService.getMessage(MessageKey.NOTIFICATION_PLAN_CANCELLED,
                    new Object[] { chefName, plan.getId(), crisisCode });
            persistentNotificationService.notifyUsersOfType(NotificationType.WEEKLY_PLAN_CANCELLED,
                    title, title, plan.getId(), uniqueRecipients);
        }
    }
}
