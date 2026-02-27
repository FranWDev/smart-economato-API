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

        @NotBlank(message = "{validation.userRequestDTO.name.notBlank}")
        @Size(min = 2, max = 100, message = "{validation.userRequestDTO.name.size}")
        @Schema(description = "Nombre completo del usuario", example = "Juan Pérez", minLength = 2, maxLength = 100)
        private String name;

        @NotBlank(message = "{validation.userRequestDTO.user.notBlank}")
        @Size(max = 100, message = "{validation.userRequestDTO.user.size}")
        @Schema(description = "Usuario del sistema", example = "juan_perez")
        private String user;

        @NotBlank(message = "{validation.userRequestDTO.password.notBlank}")
        @Size(min = 6, message = "{validation.userRequestDTO.password.size}")
        @Schema(description = "Contraseña del usuario", example = "123456", minLength = 6)
        private String password;

        @Schema(description = "Rol del usuario. Puede ser ADMIN, CHEF, ELEVATED o USER", allowableValues = { "ADMIN", "CHEF",
                        "USER" }, example = "USER", defaultValue = "USER")
        private Role role;

        @Schema(description = "ID del profesor asignado al usuario", example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Integer teacherId;
}
