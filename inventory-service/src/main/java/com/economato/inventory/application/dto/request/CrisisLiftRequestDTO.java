package com.economato.inventory.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para levantar una cuarentena alimentaria")
public class CrisisLiftRequestDTO {

    @NotEmpty(message = "Debe especificar al menos un producto")
    @Schema(description = "Lista de IDs de productos a los que se les levantará la cuarentena")
    private List<Integer> productIds;

    @NotNull(message = "El porcentaje de disponibilidad es obligatorio")
    @DecimalMin(value = "0.0", message = "El porcentaje mínimo es 0")
    @DecimalMax(value = "100.0", message = "El porcentaje máximo es 100")
    @Schema(description = "Nuevo porcentaje de disponibilidad para los productos (normalmente 100)")
    private BigDecimal availabilityPercentage;
}
