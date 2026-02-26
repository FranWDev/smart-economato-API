package com.economato.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.economato.inventory.model.Role;

import io.swagger.v3.oas.annotations.media.Schema;

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

    @Schema(description = "Rol del usuario. Puede ser ADMIN, CHEF, ELEVATED o USER", allowableValues = { "ADMIN", "CHEF",
            "USER" }, example = "USER")
    private Role role;

    @Schema(description = "Resumen del profesor asignado al usuario (si lo tuviera)")
    private UserSummaryDTO teacher;
}
