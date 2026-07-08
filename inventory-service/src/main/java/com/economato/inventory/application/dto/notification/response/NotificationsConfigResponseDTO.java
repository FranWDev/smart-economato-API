package com.economato.inventory.application.dto.notification.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationsConfigResponseDTO {
    private boolean notifyWeeklyPlanCreated;
    private boolean notifyWeeklyPlanActivated;
    private boolean notifyWeeklyPlanSlotConfirmed;
    private boolean notifyWeeklyPlanDayConfirmed;
    private boolean notifyWeeklyPlanCompleted;
    private boolean notifyWeeklyPlanCancelled;
    private boolean notifyFoodCrisisActivated;
    private boolean notifyFoodCrisisLifted;
    private boolean notifyStockPredictionTriggered;
    private boolean notifyIncidentCreated;
    private boolean notifyIncidentOpened;
    private boolean notifyIncidentClosed;
    private boolean notifyIncidentChatMessage;
    private Integer notificationRetentionDays;
    private boolean notificationAutoCleanupEnabled;
    private long totalNotificationCount;
}
