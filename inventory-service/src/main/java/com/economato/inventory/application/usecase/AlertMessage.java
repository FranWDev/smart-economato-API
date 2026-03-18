package com.economato.inventory.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Mensaje de alerta enviado por WebSocket al frontend.
 * Contiene un código de alerta (definido en AlertCode), una descripción legible
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
