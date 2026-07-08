package com.economato.inventory.application.mapper.incident;

import com.economato.inventory.application.dto.incident.response.IncidentChatReadReceiptResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentChatMessageResponseDTO;
import com.economato.inventory.domain.model.incident.IncidentChatReadReceipt;
import com.economato.inventory.domain.model.incident.IncidentChatMessage;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IncidentChatMessageMapper {

    public IncidentChatMessageResponseDTO toResponseDTO(IncidentChatMessage message) {
    return toResponseDTO(message, Collections.emptyList());
    }

    public IncidentChatMessageResponseDTO toResponseDTO(IncidentChatMessage message,
                            List<IncidentChatReadReceipt> readReceipts) {
        if (message == null) {
            return null;
        }

    List<IncidentChatReadReceiptResponseDTO> readBy = readReceipts == null ? Collections.emptyList() : readReceipts.stream()
        .filter(receipt -> receipt != null && receipt.getUser() != null)
        .filter(receipt -> message.getId() != null
            && receipt.getLastReadMessageId() != null
            && message.getId() <= receipt.getLastReadMessageId())
        .map(receipt -> IncidentChatReadReceiptResponseDTO.builder()
            .userId(receipt.getUser().getId())
            .userName(receipt.getUser().getName())
            .lastReadMessageId(receipt.getLastReadMessageId())
            .readAt(receipt.getReadAt())
            .build())
        .collect(Collectors.toList());

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
                .readBy(readBy)
                .build();
    }
}
