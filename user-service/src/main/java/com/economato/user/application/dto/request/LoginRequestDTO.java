package com.economato.user.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para la solicitud de inicio de sesión, contiene las credenciales del usuario.")
public class LoginRequestDTO {

    @NotBlank(message = "{validation.loginRequestDTO.name.notBlank}")
    @JsonAlias("username")
    @Schema(description = "Nombre de usuario o correo electrónico del usuario", example = "juanperez")
    private String name;

    @NotBlank(message = "{validation.loginRequestDTO.password.notBlank}")
    @Schema(description = "Contraseña del usuario", example = "ContraseñaSegura123!")
    private String password;
}
