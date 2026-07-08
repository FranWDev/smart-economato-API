package com.economato.inventory.application.dto.user.request;

import com.economato.inventory.domain.model.ai.AiProvider;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear o actualizar una API key global por proveedor")
public class GlobalApiKeyRequestDTO {

    @NotBlank(message = "{validation.globalApiKeyRequestDTO.provider.notBlank}")
    @Schema(description = "Proveedor AI", example = "OPENAI")
    private String provider;

    @NotBlank(message = "{validation.globalApiKeyRequestDTO.apiKey.notBlank}")
    @Schema(description = "API key en texto plano", example = "sk-...", minLength = 1)
    private String apiKey;

    public AiProvider providerAsEnum() {
        return AiProvider.valueOf(provider.trim().toUpperCase());
    }
}