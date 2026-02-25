package com.economato.inventory.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleEscalationRequestDTO {

    @NotNull(message = "La duración en minutos es obligatoria")
    @Min(value = 1, message = "La duración mínima debe ser de 1 minuto")
    @Max(value = 1440, message = "La duración máxima permitida es de 1440 minutos (24 horas)")
    private Integer durationMinutes;

}
