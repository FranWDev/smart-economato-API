package com.economato.user.application.dto.response;

import com.economato.user.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO resumen de los datos de un usuario")
public class UserSummaryDTO {

    @Schema(description = "Identificador único del usuario", example = "1")
    private Integer id;

    @Schema(description = "Nombre completo del usuario", example = "Juan Pérez")
    private String name;

    @Schema(description = "Usuario del sistema", example = "juan_perez")
    private String user;

    @Schema(description = "Rol del usuario", example = "ADMIN")
    private Role role;
}
