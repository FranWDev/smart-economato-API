package com.economato.inventory.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "DTO para cambiar la contraseña del usuario")
public class ChangePasswordRequestDTO {

    @Schema(description = "Contraseña actual (requerida solo si no es admin y no es primer login)", example = "oldPassword123")
    private String oldPassword;

    @NotBlank(message = "{validation.changePasswordRequestDTO.newPassword.notBlank}")
    @Schema(description = "Nueva contraseña", example = "newPassword123", minLength = 6, requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
