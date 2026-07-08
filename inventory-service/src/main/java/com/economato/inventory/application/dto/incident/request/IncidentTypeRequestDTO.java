package com.economato.inventory.application.dto.incident.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentTypeRequestDTO {

    @NotBlank(message = "{validation.incidentTypeRequestDTO.name.notBlank}")
    @Size(max = 100, message = "{validation.incidentTypeRequestDTO.name.size}")
    private String name;

    @Size(max = 500, message = "{validation.incidentTypeRequestDTO.description.size}")
    private String description;
}
