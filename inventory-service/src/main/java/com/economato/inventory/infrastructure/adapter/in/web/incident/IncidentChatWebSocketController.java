package com.economato.inventory.infrastructure.adapter.in.web.incident;

import com.economato.inventory.application.dto.incident.request.IncidentChatMessageRequestDTO;
import com.economato.inventory.application.dto.incident.request.IncidentChatTypingRequestDTO;
import jakarta.validation.Valid;
import com.economato.inventory.application.usecase.incident.IncidentChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class IncidentChatWebSocketController {

    private final IncidentChatService incidentChatService;

    @MessageMapping("/incidents/{incidentId}/chat.send")
    public void send(@DestinationVariable Long incidentId,
                     @Valid @Payload IncidentChatMessageRequestDTO request,
                     Authentication authentication) {
        incidentChatService.sendChatMessageWebSocket(incidentId, request.getContent(), authentication);
    }

    @MessageMapping("/incidents/{incidentId}/chat.markRead")
    public void markAsRead(@DestinationVariable Long incidentId, Authentication authentication) {
        incidentChatService.markMessagesAsReadWebSocket(incidentId, authentication);
    }

    @MessageMapping("/incidents/{incidentId}/chat.typing")
    public void typing(@DestinationVariable Long incidentId,
                       @Payload IncidentChatTypingRequestDTO request,
                       Authentication authentication) {
        incidentChatService.broadcastTypingWebSocket(incidentId, request.isTyping(), authentication);
    }
}
