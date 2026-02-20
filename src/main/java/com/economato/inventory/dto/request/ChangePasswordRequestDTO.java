package com.economato.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "DTO para cambiar la contraseña del usuario")
public class ChangePasswordRequestDTO {

    @Schema(description = "Contraseña actual (requerida solo si no es admin y no es primer login)", example = "oldPassword123")
    private String oldPassword;

    @NotBlank(message = "La nueva contraseña no puede estar vacía")
    @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres")
    @Schema(description = "Nueva contraseña", example = "newPassword123", minLength = 6, requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
