package com.economato.inventory.application.dto.user.request;

import com.economato.inventory.domain.model.user.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear o actualizar un usuario")
public class UserRequestDTO {

        public interface OnCreate {}

        public interface OnUpdate {}

        @NotBlank(message = "{validation.userRequestDTO.name.notBlank}", groups = {OnCreate.class, OnUpdate.class})
        @Size(min = 2, max = 100, message = "{validation.userRequestDTO.name.size}", groups = {OnCreate.class, OnUpdate.class})
        @Schema(description = "Nombre completo del usuario", example = "Juan Pérez", minLength = 2, maxLength = 100)
        private String name;

        @NotBlank(message = "{validation.userRequestDTO.user.notBlank}", groups = {OnCreate.class, OnUpdate.class})
        @Size(max = 100, message = "{validation.userRequestDTO.user.size}", groups = {OnCreate.class, OnUpdate.class})
        @Schema(description = "Usuario del sistema", example = "juan_perez")
        private String user;

        @NotBlank(message = "{validation.userRequestDTO.password.notBlank}", groups = OnCreate.class)
        @Schema(description = "Contraseña del usuario", example = "123456", minLength = 6,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private String password;

        @Schema(description = "Rol del usuario. Puede ser ADMIN, CHEF, ELEVATED o USER", allowableValues = { "ADMIN", "CHEF",
                        "USER" }, example = "USER", defaultValue = "USER")
        private Role role;

        @Schema(description = "ID del profesor asignado al usuario", example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Integer teacherId;
}
