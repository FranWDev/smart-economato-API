package com.economato.inventory.application.dto.weeklyplan.response;

import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanSlotResponseDTO {
    private Long id;
    private Integer recipeId;
    private String recipeName;
    private BigDecimal quantity;
    private Integer dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer sortOrder;
    private WeeklyPlanSlotStatus status;
    private LocalDateTime confirmedAt;
    private String confirmedByName;
    private List<WeeklyPlanSlotStudentResponseDTO> students;
}
