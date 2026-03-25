package com.economato.inventory.application.dto.response;

import com.economato.inventory.domain.model.WeeklyPlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmDayResponseDTO {
    private Long planId;
    private Integer dayOfWeek;
    private WeeklyPlanStatus planStatus;
    private List<WeeklyPlanSlotResponseDTO> confirmedSlots;
    private int totalSlotsConfirmed;
}
