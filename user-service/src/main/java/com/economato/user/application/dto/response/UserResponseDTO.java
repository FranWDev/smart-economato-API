package com.economato.user.application.dto.response;

import com.economato.user.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de respuesta con los datos de un usuario")
public class UserResponseDTO {

    @Schema(description = "Identificador único del usuario", example = "1")
    private Integer id;

    @Schema(description = "Nombre completo del usuario", example = "Juan Pérez")
    private String name;

    @Schema(description = "Usuario del sistema", example = "juan_perez")
    private String user;

    @Schema(description = "Indica si es el primer inicio de sesión", example = "true")
    private boolean isFirstLogin;

    @Schema(description = "Indica si el usuario está oculto", example = "false")
    private boolean isHidden;

    @Schema(description = "Rol del usuario", example = "USER")
    private Role role;

    @Schema(description = "Resumen del profesor asignado al usuario")
    private UserSummaryDTO teacher;

    @Schema(description = "Token JWT del usuario", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;
}
