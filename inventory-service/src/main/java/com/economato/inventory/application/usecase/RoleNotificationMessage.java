package com.economato.inventory.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDateTime;

/**
 * WebSocket notification message sent to specific roles.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleNotificationMessage {
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("message")
    private String message;

    @JsonProperty("code")
    private AlertCode code;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    public RoleNotificationMessage(String title, String message) {
        this.title = title;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
