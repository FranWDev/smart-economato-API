package com.economato.inventory.application.dto.notification.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceUpdateRequest {

    @NotBlank
    private String screen;

    private String context;

    private boolean heartbeat;
}
