package com.economato.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.economato.inventory.model.Role;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear o actualizar un usuario")
public class UserRequestDTO {

        @NotBlank(message = "{userrequestdto.notblank.el.nombre.no.puede.estar.vac.o}")
        @Size(min = 2, max = 100, message = "{userrequestdto.size.el.nombre.debe.tener.entre.2.y}")
        @Schema(description = "Nombre completo del usuario", example = "Juan Pérez", minLength = 2, maxLength = 100)
        private String name;

        @NotBlank(message = "{userrequestdto.notblank.el.usuario.no.puede.estar.vac.}")
        @Size(max = 100, message = "{userrequestdto.size.el.usuario.no.puede.exceder.10}")
        @Schema(description = "Usuario del sistema", example = "juan_perez")
        private String user;

        @NotBlank(message = "{userrequestdto.notblank.la.contrase.a.no.puede.estar.v}")
        @Size(min = 6, message = "{userrequestdto.size.la.contrase.a.debe.tener.al.me}")
        @Schema(description = "Contraseña del usuario", example = "123456", minLength = 6)
        private String password;

        @Schema(description = "Rol del usuario. Puede ser ADMIN, CHEF, ELEVATED o USER", allowableValues = { "ADMIN", "CHEF",
                        "USER" }, example = "USER", defaultValue = "USER")
        private Role role;

        @Schema(description = "ID del profesor asignado al usuario", example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Integer teacherId;
}
