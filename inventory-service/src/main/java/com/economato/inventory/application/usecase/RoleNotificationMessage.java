package com.economato.inventory.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WebSocket notification message sent to specific roles.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RoleNotificationMessage {
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private long timestamp;

    public RoleNotificationMessage(String title, String message) {
        this.title = title;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
}
