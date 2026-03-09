package com.economato.inventory.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Schema(description = "DTO para la solicitud de inicio de sesión, contiene las credenciales del usuario.")
public class LoginRequestDTO {

    @NotBlank(message = "{validation.loginRequestDTO.name.notBlank}")
    @JsonAlias("username")
    @Schema(description = "Nombre de usuario o correo electrónico del usuario (aceptable como 'name' o 'username')", example = "juanperez")
    private String name;

    @NotBlank(message = "{validation.loginRequestDTO.password.notBlank}")
    @Schema(description = "Contraseña del usuario", example = "ContraseñaSegura123!")
    private String password;
}
