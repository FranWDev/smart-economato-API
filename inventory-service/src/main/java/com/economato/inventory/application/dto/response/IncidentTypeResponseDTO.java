package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentTypeResponseDTO {
    private Integer id;
    private String name;
    private String description;
    private boolean isActive;
}
