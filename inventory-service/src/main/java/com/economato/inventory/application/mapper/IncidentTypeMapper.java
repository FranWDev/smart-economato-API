package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.IncidentTypeResponseDTO;
import com.economato.inventory.domain.model.IncidentType;
import org.springframework.stereotype.Component;

@Component
public class IncidentTypeMapper {

    public IncidentTypeResponseDTO toResponseDTO(IncidentType incidentType) {
        if (incidentType == null) {
            return null;
        }
        return IncidentTypeResponseDTO.builder()
                .id(incidentType.getId())
                .name(incidentType.getName())
                .description(incidentType.getDescription())
                .isActive(incidentType.isActive())
                .build();
    }
}
