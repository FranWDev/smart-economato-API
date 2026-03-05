package com.economato.inventory.event;

import lombok.Getter;

/**
 * Event published when a new WebSocket connection is successfully authenticated.
 * Listeners can use this to send initial state notifications to the newly connected user.
 */
@Getter
public class WebSocketConnectedEvent {
    private final String username;

    public WebSocketConnectedEvent(String username) {
        this.username = username;
    }
}
