package com.economato.inventory.application.dto.request;

import com.economato.inventory.domain.model.IncidentSeverity;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenIncidentRequestDTO {

    @NotNull(message = "{validation.openIncidentRequestDTO.severity.notNull}")
    private IncidentSeverity severity;
}
