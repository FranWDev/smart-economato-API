package com.economato.inventory.application.dto.user.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta de configuración de una API key global")
public class GlobalApiKeyResponseDTO {
    private String provider;
    private String keyHint;
    private boolean active;
    private LocalDateTime createdAt;
}