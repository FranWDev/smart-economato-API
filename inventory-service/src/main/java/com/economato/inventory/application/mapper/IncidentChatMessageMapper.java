package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.IncidentChatMessageResponseDTO;
import com.economato.inventory.domain.model.IncidentChatMessage;
import org.springframework.stereotype.Component;

@Component
public class IncidentChatMessageMapper {

    public IncidentChatMessageResponseDTO toResponseDTO(IncidentChatMessage message) {
        if (message == null) {
            return null;
        }
        return IncidentChatMessageResponseDTO.builder()
                .id(message.getId())
                .authorId(message.getAuthor() != null ? message.getAuthor().getId() : null)
                .authorName(message.getAuthor() != null ? message.getAuthor().getName() : null)
                .authorRole(message.getAuthor() != null ? message.getAuthor().getRole() : null)
                .content(message.getContent())
                .hasAttachment(message.isHasAttachment())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentFilename(message.getAttachmentFilename())
                .attachmentContentType(message.getAttachmentContentType())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
