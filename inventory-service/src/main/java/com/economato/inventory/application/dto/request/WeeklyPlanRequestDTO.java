package com.economato.inventory.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanRequestDTO {

    private Integer chefId;

    @NotNull(message = "{ValidationMessages.weekly_start_date_required}")
    private LocalDate weekStartDate;

    @NotEmpty(message = "{ValidationMessages.weekly_slots_required}")
    @Valid
    private List<WeeklyPlanSlotRequestDTO> slots;
}
