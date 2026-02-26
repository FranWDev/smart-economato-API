package com.economato.inventory.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeCookingRequestDTO {

    @NotNull(message = "{recipecookingrequestdto.notnull.el.id.de.la.receta.es.obligato}")
    @Positive(message = "{recipecookingrequestdto.positive.el.id.de.la.receta.debe.ser.po}")
    private Integer recipeId;

    @NotNull(message = "{recipecookingrequestdto.notnull.la.cantidad.a.cocinar.es.oblig}")
    @DecimalMin(value = "0.001", message = "{recipecookingrequestdto.decimalmin.la.cantidad.debe.ser.mayor.a.0}")
    @Digits(integer = 10, fraction = 3, message = "{recipecookingrequestdto.digits.la.cantidad.debe.tener.m.ximo.}")
    private BigDecimal quantity;

    @Size(max = 500, message = "{recipecookingrequestdto.size.los.detalles.no.pueden.exceder}")
    private String details;
}
