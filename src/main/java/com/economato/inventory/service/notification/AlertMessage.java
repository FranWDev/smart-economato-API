package com.economato.inventory.service.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WebSocket alert message sent to frontend with error code.
 * Frontend translates the code to its user's locale.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessage {
    @JsonProperty("code")
    private String code;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("description")
    private String description;

    public AlertMessage(String code, String description) {
        this.code = code;
        this.timestamp = System.currentTimeMillis();
        this.description = description;
    }
}
