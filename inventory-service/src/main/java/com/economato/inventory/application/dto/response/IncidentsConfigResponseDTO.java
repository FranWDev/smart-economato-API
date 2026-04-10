package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentsConfigResponseDTO {
    private int maxChatMessageLength;
    private int maxAdminAttachableAudits;
    private long maxUploadFileSizeBytes;
    private String allowedFileTypes;
}
