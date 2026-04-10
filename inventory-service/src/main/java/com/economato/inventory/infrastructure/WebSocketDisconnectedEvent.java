package com.economato.inventory.infrastructure;

import lombok.Getter;

@Getter
public class WebSocketDisconnectedEvent {

    private final String username;
    private final String sessionId;

    public WebSocketDisconnectedEvent(String username, String sessionId) {
        this.username = username;
        this.sessionId = sessionId;
    }
}
