package com.economato.inventory.application.dto.response;

import com.economato.inventory.domain.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentChatMessageResponseDTO {
    private Long id;
    private Integer authorId;
    private String authorName;
    private Role authorRole;
    private String content;
    private boolean hasAttachment;
    private String attachmentUrl;
    private String attachmentFilename;
    private String attachmentContentType;
    private LocalDateTime createdAt;
    private List<IncidentChatReadReceiptResponseDTO> readBy;
}
