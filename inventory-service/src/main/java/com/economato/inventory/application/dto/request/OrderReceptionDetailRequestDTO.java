package com.economato.inventory.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para recibir un producto dentro de una orden")
public class OrderReceptionDetailRequestDTO {

    @NotNull(message = "{validation.orderReceptionDetailRequestDTO.productId.notNull}")
    @Schema(description = "Identificador del producto", example = "42")
    private Integer productId;

    @NotNull(message = "{validation.orderReceptionDetailRequestDTO.quantityReceived.notNull}")
    @PositiveOrZero(message = "{validation.orderReceptionDetailRequestDTO.quantityReceived.positiveOrZero}")
    @Schema(description = "Cantidad del producto recibida", example = "5.0")
    private BigDecimal quantityReceived;

    @NotNull(message = "{validation.orderReceptionDetailRequestDTO.expirationDate.notNull}")
    @FutureOrPresent(message = "{validation.orderReceptionDetailRequestDTO.expirationDate.futureOrPresent}")
    @Schema(description = "Fecha de caducidad del lote recibido (obligatoria)", example = "2026-12-31")
    private LocalDate expirationDate;
}
