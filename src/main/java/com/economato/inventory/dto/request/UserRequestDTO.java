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

        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nombre completo del usuario", example = "Juan Pérez", minLength = 2, maxLength = 100)
        private String name;

        @NotBlank(message = "El usuario no puede estar vacío")
        @Size(max = 100, message = "El usuario no puede exceder 100 caracteres")
        @Schema(description = "Usuario del sistema", example = "juan_perez")
        private String user;

        @NotBlank(message = "La contraseña no puede estar vacía")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        @Schema(description = "Contraseña del usuario", example = "123456", minLength = 6)
        private String password;

        @Schema(description = "Rol del usuario. Puede ser ADMIN, CHEF o USER", allowableValues = { "ADMIN", "CHEF",
                        "USER" }, example = "USER", defaultValue = "USER")
        private Role role;

        @Schema(description = "ID del profesor asignado al usuario", example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Integer teacherId;
}
