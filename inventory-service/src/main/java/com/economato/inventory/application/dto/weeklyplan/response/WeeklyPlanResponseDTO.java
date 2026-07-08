package com.economato.inventory.application.dto.weeklyplan.response;

import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanResponseDTO {
    private Long id;
    private Integer chefId;
    private String chefName;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private WeeklyPlanStatus status;
    private List<WeeklyPlanSlotResponseDTO> slots;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
