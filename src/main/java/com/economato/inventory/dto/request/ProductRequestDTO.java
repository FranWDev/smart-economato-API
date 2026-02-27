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

    @NotBlank(message = "{validation.productRequestDTO.name.notBlank}")
    @Size(min = 2, max = 100, message = "{validation.productRequestDTO.name.size}")
    @Schema(description = "Nombre del producto", example = "Harina de trigo")
    private String name;

    @Schema(description = "Tipo o categoría del producto", example = "Ingrediente")
    private String type;

    @NotBlank(message = "{validation.productRequestDTO.unit.notBlank}")
    @Size(max = 20, message = "{validation.productRequestDTO.unit.size}")
    @Schema(description = "Unidad de medida del producto", example = "kg")
    private String unit;

    @NotNull(message = "{validation.productRequestDTO.unitPrice.notNull}")
    @DecimalMin(value = "0.01", message = "{validation.productRequestDTO.unitPrice.decimalMin}")
    @Digits(integer = 10, fraction = 2, message = "{validation.productRequestDTO.unitPrice.digits}")
    @Schema(description = "Precio por unidad de medida", example = "1.50")
    @JsonAlias("price")
    private BigDecimal unitPrice;

    @NotBlank(message = "{validation.productRequestDTO.productCode.notBlank}")
    @Size(max = 50, message = "{validation.productRequestDTO.productCode.size}")
    @Schema(description = "Código único del producto", example = "92438232374")
    private String productCode;

    @NotNull(message = "{validation.productRequestDTO.currentStock.notNull}")
    @DecimalMin(value = "0.0", inclusive = true, message = "{validation.productRequestDTO.currentStock.decimalMin}")
    @Digits(integer = 10, fraction = 3, message = "{validation.productRequestDTO.currentStock.digits}")
    @Schema(description = "Cantidad actual en inventario", example = "250.00")
    @JsonAlias("stock")
    private BigDecimal currentStock;

    @DecimalMin(value = "0.00", message = "{validation.productRequestDTO.availabilityPercentage.decimalMin}")
    @DecimalMax(value = "100.00", message = "{validation.productRequestDTO.availabilityPercentage.decimalMax}")
    @Digits(integer = 3, fraction = 2, message = "{validation.productRequestDTO.availabilityPercentage.digits}")
    @Schema(description = "Porcentaje de disponibilidad del producto (0-100). Si no se especifica, se asume 100%", example = "85.50")
    private BigDecimal availabilityPercentage;

    @NotNull(message = "{validation.productRequestDTO.minimumStock.notNull}")
    @DecimalMin(value = "0.0", inclusive = true, message = "{validation.productRequestDTO.minimumStock.decimalMin}")
    @Digits(integer = 10, fraction = 3, message = "{validation.productRequestDTO.minimumStock.digits}")
    @Schema(description = "Stock mínimo antes de alerta", example = "10.00")
    @JsonAlias("minStock")
    private BigDecimal minimumStock;

    @Schema(description = "ID del proveedor del producto", example = "1")
    private Integer supplierId;
}
