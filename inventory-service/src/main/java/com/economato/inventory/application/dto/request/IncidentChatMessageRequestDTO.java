package com.economato.inventory.application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentChatMessageRequestDTO {

    @Size(max = 5000, message = "{validation.incidentChatMessageRequestDTO.content.size}")
    private String content;
}
