package com.economato.inventory.infrastructure.adapter.in.web;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.economato.inventory.application.dto.request.PresenceUpdateRequest;
import com.economato.inventory.application.usecase.UserPresenceService;

@ExtendWith(MockitoExtension.class)
class PresenceWebSocketControllerTest {

    @Mock
    private UserPresenceService userPresenceService;

    @InjectMocks
    private PresenceWebSocketController controller;

    @Test
    void updatePresence_delegatesToService() {
        SimpMessageHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session-1");
        accessor.setUser(new UsernamePasswordAuthenticationToken("studentUser", null));

        PresenceUpdateRequest request = new PresenceUpdateRequest("ORDER_RECEPTION", "Orden #6", false);

        controller.updatePresence(accessor, request);

        verify(userPresenceService).updateActivity("studentUser", "session-1", "ORDER_RECEPTION", "Orden #6", false);
    }
}
