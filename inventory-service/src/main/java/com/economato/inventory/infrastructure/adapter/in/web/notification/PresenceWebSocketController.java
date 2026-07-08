package com.economato.inventory.infrastructure.adapter.in.web.notification;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.economato.inventory.application.dto.notification.request.PresenceUpdateRequest;
import com.economato.inventory.application.usecase.notification.UserPresenceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PresenceWebSocketController {

    private final UserPresenceService presenceService;

    @MessageMapping("/presence.update")
    public void updatePresence(SimpMessageHeaderAccessor headerAccessor,
            @Valid @Payload PresenceUpdateRequest request) {
        String username = headerAccessor.getUser().getName();
        String sessionId = headerAccessor.getSessionId();

        presenceService.updateActivity(username, sessionId,
                request.getScreen(), request.getContext(), request.isHeartbeat());
    }
}
