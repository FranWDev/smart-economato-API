package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentChatTypingResponseDTO {
    private Long incidentId;
    private Integer userId;
    private String userName;
    private boolean typing;
}
