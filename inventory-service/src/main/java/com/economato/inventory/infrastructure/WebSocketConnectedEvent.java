package com.economato.inventory.infrastructure;

import lombok.Getter;

/**
 * Evento publicado cuando un cliente se conecta a través de WebSocket, 
 * para que el frontend pueda mostrar un mensaje de bienvenida o actualizar la lista de usuarios conectados
 */
@Getter
public class WebSocketConnectedEvent {
    private final String username;

    public WebSocketConnectedEvent(String username) {
        this.username = username;
    }
}
