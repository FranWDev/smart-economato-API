package com.economato.inventory.application.dto.incident.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloseIncidentRequestDTO {

    private boolean hasResolution;

    @Size(max = 5000, message = "{validation.closeIncidentRequestDTO.resolution.size}")
    private String resolution;
}
