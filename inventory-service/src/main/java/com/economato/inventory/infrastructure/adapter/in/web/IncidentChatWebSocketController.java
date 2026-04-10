package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.IncidentChatMessageRequestDTO;
import com.economato.inventory.application.dto.request.IncidentChatTypingRequestDTO;
import jakarta.validation.Valid;
import com.economato.inventory.application.usecase.IncidentChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class IncidentChatWebSocketController {

    private final IncidentChatService incidentChatService;

    @MessageMapping("/incidents/{incidentId}/chat.send")
    public void send(@DestinationVariable Long incidentId,
                     @Valid @Payload IncidentChatMessageRequestDTO request,
                     Authentication authentication) {
        withAuthentication(authentication, () -> incidentChatService.sendMessage(incidentId, request.getContent(), null));
    }

    @MessageMapping("/incidents/{incidentId}/chat.markRead")
    public void markAsRead(@DestinationVariable Long incidentId, Authentication authentication) {
        withAuthentication(authentication, () -> incidentChatService.markMessagesAsRead(incidentId));
    }

    @MessageMapping("/incidents/{incidentId}/chat.typing")
    public void typing(@DestinationVariable Long incidentId,
                       @Payload IncidentChatTypingRequestDTO request,
                       Authentication authentication) {
        withAuthentication(authentication, () -> incidentChatService.broadcastTyping(incidentId, request.isTyping()));
    }

    private void withAuthentication(Authentication authentication, Runnable action) {
        if (authentication == null || authentication.getAuthorities() == null || authentication.getAuthorities().stream().noneMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_CHEF".equals(authority.getAuthority())
                        || "ROLE_ELEVATED".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Forbidden");
        }

        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previous != null) {
                SecurityContextHolder.getContext().setAuthentication(previous);
            } else {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
