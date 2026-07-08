package com.economato.inventory.application.dto.weeklyplan.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Datos para crear o actualizar un slot dentro de un plan semanal")
public class WeeklyPlanSlotRequestDTO {

    @NotNull(message = "{ValidationMessages.recipe_id_required}")
    @Schema(description = "ID de la receta a cocinar", example = "1")
    private Integer recipeId;

    @NotNull(message = "{ValidationMessages.quantity_required}")
    @DecimalMin(value = "0.001", message = "{ValidationMessages.quantity_min}")
    @Schema(description = "Cantidad o raciones a producir", example = "10.5")
    private BigDecimal quantity;

    @NotNull(message = "{ValidationMessages.day_of_week_required}")
    @Min(value = 1, message = "{ValidationMessages.day_of_week_min}")
    @Max(value = 7, message = "{ValidationMessages.day_of_week_max}")
    @Schema(description = "Día de la semana (1=Lunes, 7=Domingo)", example = "1")
    private Integer dayOfWeek;

    @NotNull(message = "{ValidationMessages.start_time_required}")
    @Schema(description = "Hora de inicio del slot", example = "10:00")
    private LocalTime startTime;

    @NotNull(message = "{ValidationMessages.end_time_required}")
    @Schema(description = "Hora de fin del slot", example = "11:30")
    private LocalTime endTime;

    @NotNull(message = "{ValidationMessages.sort_order_required}")
    @Schema(description = "Orden visual en el calendario", example = "1")
    private Integer sortOrder;

    @Schema(description = "Identificador único del slot (opcional, para actualizaciones)", example = "123")
    private Long id;

    @Schema(description = "Lista de IDs de alumnos asignados", example = "[1, 2, 3]")
    private List<Integer> studentIds;
}
