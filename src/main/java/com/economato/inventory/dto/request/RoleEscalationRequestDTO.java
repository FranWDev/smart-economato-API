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

    @NotNull(message = "{validation.roleEscalationRequestDTO.durationMinutes.notNull}")
    @Min(value = 1, message = "{validation.roleEscalationRequestDTO.durationMinutes.min}")
    @Max(value = 1440, message = "{validation.roleEscalationRequestDTO.durationMinutes.max}")
    private Integer durationMinutes;

}
