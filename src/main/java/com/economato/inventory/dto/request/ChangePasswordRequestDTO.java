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

    @NotBlank(message = "{validation.changePasswordRequestDTO.newPassword.notBlank}")
    @Size(min = 6, message = "{validation.changePasswordRequestDTO.newPassword.size}")
    @Schema(description = "Nueva contraseña", example = "newPassword123", minLength = 6, requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
