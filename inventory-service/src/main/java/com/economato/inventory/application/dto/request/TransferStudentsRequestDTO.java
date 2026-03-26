package com.economato.inventory.application.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para transferir alumnos de un profesor a otro")
public class TransferStudentsRequestDTO {

    @NotNull(message = "{validation.transferStudentsRequestDTO.fromTeacherId.notNull}")
    @Schema(description = "ID del profesor origen", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer fromTeacherId;

    @NotNull(message = "{validation.transferStudentsRequestDTO.toTeacherId.notNull}")
    @Schema(description = "ID del profesor destino", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer toTeacherId;

    @NotEmpty(message = "{validation.transferStudentsRequestDTO.studentIds.notEmpty}")
    @Schema(description = "Lista de IDs de alumnos a transferir", example = "[3, 4]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Integer> studentIds;
}
