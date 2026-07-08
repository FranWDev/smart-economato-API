package com.economato.inventory.application.dto.incident.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateIncidentRequestDTO {

    @NotNull(message = "{validation.createIncidentRequestDTO.incidentTypeId.notNull}")
    @Positive(message = "{validation.createIncidentRequestDTO.incidentTypeId.positive}")
    private Integer incidentTypeId;

    @NotBlank(message = "{validation.createIncidentRequestDTO.title.notBlank}")
    @Size(max = 255, message = "{validation.createIncidentRequestDTO.title.size}")
    private String title;

    @NotBlank(message = "{validation.createIncidentRequestDTO.description.notBlank}")
    @Size(max = 5000, message = "{validation.createIncidentRequestDTO.description.size}")
    private String description;

    private List<Long> cookingAuditIds;
}
