package com.economato.inventory.application.dto.notification.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationsConfigRequestDTO {

    @NotNull private Boolean notifyWeeklyPlanCreated;
    @NotNull private Boolean notifyWeeklyPlanActivated;
    @NotNull private Boolean notifyWeeklyPlanSlotConfirmed;
    @NotNull private Boolean notifyWeeklyPlanDayConfirmed;
    @NotNull private Boolean notifyWeeklyPlanCompleted;
    @NotNull private Boolean notifyWeeklyPlanCancelled;
    @NotNull private Boolean notifyFoodCrisisActivated;
    @NotNull private Boolean notifyFoodCrisisLifted;
    @NotNull private Boolean notifyStockPredictionTriggered;
    @NotNull private Boolean notifyIncidentCreated;
    @NotNull private Boolean notifyIncidentOpened;
    @NotNull private Boolean notifyIncidentClosed;
    @NotNull private Boolean notifyIncidentChatMessage;

    @NotNull
    private Boolean notificationAutoCleanupEnabled;

    @Min(1) @Max(3650)
    private Integer notificationRetentionDays;
}
