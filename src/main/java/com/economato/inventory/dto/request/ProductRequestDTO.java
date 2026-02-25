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

    @NotBlank(message = "El nombre del producto no puede estar vacío")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Schema(description = "Nombre del producto", example = "Harina de trigo")
    private String name;

    @Schema(description = "Tipo o categoría del producto", example = "Ingrediente")
    private String type;

    @NotBlank(message = "La unidad de medida no puede estar vacía")
    @Size(max = 20, message = "La unidad no puede exceder 20 caracteres")
    @Schema(description = "Unidad de medida del producto", example = "kg")
    private String unit;

    @NotNull(message = "El precio unitario no puede ser nulo")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
    @Digits(integer = 10, fraction = 2, message = "El precio debe tener máximo 10 dígitos enteros y 2 decimales")
    @Schema(description = "Precio por unidad de medida", example = "1.50")
    @JsonAlias("price")
    private BigDecimal unitPrice;

    @NotBlank(message = "El código del producto no puede estar vacío")
    @Size(max = 50, message = "El código no puede exceder 50 caracteres")
    @Schema(description = "Código único del producto", example = "92438232374")
    private String productCode;

    @NotNull(message = "El stock actual no puede ser nulo")
    @DecimalMin(value = "0.0", inclusive = true, message = "El stock no puede ser negativo")
    @Digits(integer = 10, fraction = 3, message = "El stock debe tener máximo 10 dígitos enteros y 3 decimales")
    @Schema(description = "Cantidad actual en inventario", example = "250.00")
    @JsonAlias("stock")
    private BigDecimal currentStock;

    @DecimalMin(value = "0.00", message = "El porcentaje de disponibilidad no puede ser negativo")
    @DecimalMax(value = "100.00", message = "El porcentaje de disponibilidad no puede ser mayor a 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje debe tener máximo 3 dígitos enteros y 2 decimales")
    @Schema(description = "Porcentaje de disponibilidad del producto (0-100). Si no se especifica, se asume 100%", example = "85.50")
    private BigDecimal availabilityPercentage;

    @NotNull(message = "El stock mínimo no puede ser nulo")
    @DecimalMin(value = "0.0", inclusive = true, message = "El stock mínimo no puede ser negativo")
    @Digits(integer = 10, fraction = 3, message = "El stock mínimo debe tener máximo 10 dígitos enteros y 3 decimales")
    @Schema(description = "Stock mínimo antes de alerta", example = "10.00")
    @JsonAlias("minStock")
    private BigDecimal minimumStock;

    @Schema(description = "ID del proveedor del producto", example = "1")
    private Integer supplierId;
}
