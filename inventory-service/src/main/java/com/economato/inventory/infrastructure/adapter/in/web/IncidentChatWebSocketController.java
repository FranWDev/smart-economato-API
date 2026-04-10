package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.IncidentChatMessageRequestDTO;
import com.economato.inventory.application.dto.request.IncidentChatTypingRequestDTO;
import jakarta.validation.Valid;
import com.economato.inventory.application.usecase.IncidentChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class IncidentChatWebSocketController {

    private final IncidentChatService incidentChatService;

    @MessageMapping("/incidents/{incidentId}/chat.send")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    public void send(@DestinationVariable Long incidentId,
                     @Valid @Payload IncidentChatMessageRequestDTO request) {
        incidentChatService.sendMessage(incidentId, request.getContent(), null);
    }

    @MessageMapping("/incidents/{incidentId}/chat.markRead")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    public void markAsRead(@DestinationVariable Long incidentId) {
        incidentChatService.markMessagesAsRead(incidentId);
    }

    @MessageMapping("/incidents/{incidentId}/chat.typing")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    public void typing(@DestinationVariable Long incidentId,
                       @Payload IncidentChatTypingRequestDTO request) {
        incidentChatService.broadcastTyping(incidentId, request.isTyping());
    }
}
