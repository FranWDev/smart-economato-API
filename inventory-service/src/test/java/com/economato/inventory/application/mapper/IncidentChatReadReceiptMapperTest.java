package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.ChatReadReceiptBroadcastDTO;
import com.economato.inventory.application.dto.response.IncidentChatReadReceiptResponseDTO;
import com.economato.inventory.domain.model.Incident;
import com.economato.inventory.domain.model.IncidentChatReadReceipt;
import com.economato.inventory.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IncidentChatReadReceiptMapperTest {

    private final IncidentChatReadReceiptMapper mapper = new IncidentChatReadReceiptMapper();

    @Test
    void toResponseDTO_ShouldMapAllFields() {
        IncidentChatReadReceipt receipt = receipt();

        IncidentChatReadReceiptResponseDTO dto = mapper.toResponseDTO(receipt);

        assertNotNull(dto);
        assertEquals(2, dto.getUserId());
        assertEquals("Creator", dto.getUserName());
        assertEquals(10L, dto.getLastReadMessageId());
        assertEquals(receipt.getReadAt(), dto.getReadAt());
    }

    @Test
    void toBroadcastDTO_ShouldMapAllFields() {
        IncidentChatReadReceipt receipt = receipt();

        ChatReadReceiptBroadcastDTO dto = mapper.toBroadcastDTO(receipt, 100L);

        assertNotNull(dto);
        assertEquals(100L, dto.getIncidentId());
        assertEquals(2, dto.getUserId());
        assertEquals("Creator", dto.getUserName());
        assertEquals(10L, dto.getLastReadMessageId());
        assertEquals(receipt.getReadAt(), dto.getReadAt());
    }

    private IncidentChatReadReceipt receipt() {
        User user = new User();
        user.setId(2);
        user.setName("Creator");

        Incident incident = Incident.builder().id(100L).build();

        return IncidentChatReadReceipt.builder()
                .id(1L)
                .incident(incident)
                .user(user)
                .lastReadMessageId(10L)
                .readAt(LocalDateTime.of(2026, 4, 10, 8, 45))
                .build();
    }
}
