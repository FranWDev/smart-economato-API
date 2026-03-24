package com.economato.inventory.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanSlotRequestDTO {

    @NotNull(message = "{ValidationMessages.recipe_id_required}")
    private Integer recipeId;

    @NotNull(message = "{ValidationMessages.quantity_required}")
    @DecimalMin(value = "0.001", message = "{ValidationMessages.quantity_min}")
    private BigDecimal quantity;

    @NotNull(message = "{ValidationMessages.day_of_week_required}")
    @Min(value = 1, message = "{ValidationMessages.day_of_week_min}")
    @Max(value = 7, message = "{ValidationMessages.day_of_week_max}")
    private Integer dayOfWeek;

    @NotNull(message = "{ValidationMessages.start_time_required}")
    private LocalTime startTime;

    @NotNull(message = "{ValidationMessages.end_time_required}")
    private LocalTime endTime;

    @NotNull(message = "{ValidationMessages.sort_order_required}")
    private Integer sortOrder;

    private List<Integer> studentIds;
}
