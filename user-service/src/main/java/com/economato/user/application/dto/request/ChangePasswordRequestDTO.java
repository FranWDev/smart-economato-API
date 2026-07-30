package com.economato.user.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para cambiar la contraseña del usuario")
public class ChangePasswordRequestDTO {

    @Schema(description = "Contraseña actual", example = "oldPassword123")
    private String oldPassword;

    @NotBlank(message = "{validation.changePasswordRequestDTO.newPassword.notBlank}")
    @Schema(description = "Nueva contraseña", example = "newPassword123")
    private String newPassword;
}
