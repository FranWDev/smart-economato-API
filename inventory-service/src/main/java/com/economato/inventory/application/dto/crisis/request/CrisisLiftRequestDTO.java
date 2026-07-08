package com.economato.inventory.application.dto.crisis.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para levantar una cuarentena alimentaria por ID de crisis")
public class CrisisLiftRequestDTO {

    @NotNull(message = "El ID de la crisis es obligatorio")
    @Schema(description = "ID de la crisis en base de datos")
    private Long crisisId;

    @DecimalMin(value = "0.0", message = "El porcentaje mínimo es 0")
    @DecimalMax(value = "100.0", message = "El porcentaje máximo es 100")
    @Schema(description = "Si se indica, fuerza este porcentaje para todos los productos de la crisis. Si no, restaura el porcentaje original")
    private BigDecimal availabilityPercentage;
}
