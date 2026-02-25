package com.economato.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.economato.inventory.model.Role;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO resumen de los datos de un usuario (para evitar conflictos de recursividad en relaciones)")
public class UserSummaryDTO {

    @Schema(description = "Identificador único del usuario", example = "1")
    private Integer id;

    @Schema(description = "Nombre completo del usuario", example = "Juan Pérez")
    private String name;

    @Schema(description = "Usuario del sistema", example = "juan_perez")
    private String user;

    @Schema(description = "Rol del usuario. Puede ser ADMIN, CHEF o USER", allowableValues = { "ADMIN", "CHEF",
            "USER" }, example = "ADMIN")
    private Role role;
}
