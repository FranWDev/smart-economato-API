package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.ChatReadReceiptBroadcastDTO;
import com.economato.inventory.application.dto.response.IncidentChatReadReceiptResponseDTO;
import com.economato.inventory.domain.model.IncidentChatReadReceipt;
import org.springframework.stereotype.Component;

@Component
public class IncidentChatReadReceiptMapper {

    public IncidentChatReadReceiptResponseDTO toResponseDTO(IncidentChatReadReceipt receipt) {
        if (receipt == null) {
            return null;
        }

        return IncidentChatReadReceiptResponseDTO.builder()
                .userId(receipt.getUser() != null ? receipt.getUser().getId() : null)
                .userName(receipt.getUser() != null ? receipt.getUser().getName() : null)
                .lastReadMessageId(receipt.getLastReadMessageId())
                .readAt(receipt.getReadAt())
                .build();
    }

    public ChatReadReceiptBroadcastDTO toBroadcastDTO(IncidentChatReadReceipt receipt, Long incidentId) {
        if (receipt == null) {
            return null;
        }

        return ChatReadReceiptBroadcastDTO.builder()
                .incidentId(incidentId)
                .userId(receipt.getUser() != null ? receipt.getUser().getId() : null)
                .userName(receipt.getUser() != null ? receipt.getUser().getName() : null)
                .lastReadMessageId(receipt.getLastReadMessageId())
                .readAt(receipt.getReadAt())
                .build();
    }
}
