package com.economato.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para asignar o quitar un profesor a un usuario")
public class TeacherAssignmentRequestDTO {

    @Schema(description = "ID del profesor (usuario administrador) a asignar. Enviar null para desasignar.", example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer teacherId;
}
