package com.economato.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos necesarios para crear o actualizar un producto")
public class ProductRequestDTO {

    @NotBlank(message = "{productrequestdto.notblank.el.nombre.del.producto.no.pued}")
    @Size(min = 2, max = 100, message = "{productrequestdto.size.el.nombre.debe.tener.entre.2.y}")
    @Schema(description = "Nombre del producto", example = "Harina de trigo")
    private String name;

    @Schema(description = "Tipo o categoría del producto", example = "Ingrediente")
    private String type;

    @NotBlank(message = "{productrequestdto.notblank.la.unidad.de.medida.no.puede.e}")
    @Size(max = 20, message = "{productrequestdto.size.la.unidad.no.puede.exceder.20.}")
    @Schema(description = "Unidad de medida del producto", example = "kg")
    private String unit;

    @NotNull(message = "{productrequestdto.notnull.el.precio.unitario.no.puede.se}")
    @DecimalMin(value = "0.01", message = "{productrequestdto.decimalmin.el.precio.debe.ser.mayor.que.c}")
    @Digits(integer = 10, fraction = 2, message = "{productrequestdto.digits.el.precio.debe.tener.m.ximo.10}")
    @Schema(description = "Precio por unidad de medida", example = "1.50")
    @JsonAlias("price")
    private BigDecimal unitPrice;

    @NotBlank(message = "{productrequestdto.notblank.el.c.digo.del.producto.no.pued}")
    @Size(max = 50, message = "{productrequestdto.size.el.c.digo.no.puede.exceder.50.}")
    @Schema(description = "Código único del producto", example = "92438232374")
    private String productCode;

    @NotNull(message = "{productrequestdto.notnull.el.stock.actual.no.puede.ser.n}")
    @DecimalMin(value = "0.0", inclusive = true, message = "{productrequestdto.decimalmin.el.stock.no.puede.ser.negativo}")
    @Digits(integer = 10, fraction = 3, message = "{productrequestdto.digits.el.stock.debe.tener.m.ximo.10.}")
    @Schema(description = "Cantidad actual en inventario", example = "250.00")
    @JsonAlias("stock")
    private BigDecimal currentStock;

    @DecimalMin(value = "0.00", message = "{productrequestdto.decimalmin.el.porcentaje.de.disponibilida}")
    @DecimalMax(value = "100.00", message = "{productrequestdto.decimalmax.el.porcentaje.de.disponibilida}")
    @Digits(integer = 3, fraction = 2, message = "{productrequestdto.digits.el.porcentaje.debe.tener.m.xim}")
    @Schema(description = "Porcentaje de disponibilidad del producto (0-100). Si no se especifica, se asume 100%", example = "85.50")
    private BigDecimal availabilityPercentage;

    @NotNull(message = "{productrequestdto.notnull.el.stock.m.nimo.no.puede.ser.n}")
    @DecimalMin(value = "0.0", inclusive = true, message = "{productrequestdto.decimalmin.el.stock.m.nimo.no.puede.ser.n}")
    @Digits(integer = 10, fraction = 3, message = "{productrequestdto.digits.el.stock.m.nimo.debe.tener.m.x}")
    @Schema(description = "Stock mínimo antes de alerta", example = "10.00")
    @JsonAlias("minStock")
    private BigDecimal minimumStock;

    @Schema(description = "ID del proveedor del producto", example = "1")
    private Integer supplierId;
}
