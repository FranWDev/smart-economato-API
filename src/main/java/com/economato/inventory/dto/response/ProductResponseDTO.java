package com.economato.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos de respuesta de un producto")
public class ProductResponseDTO {

    @Schema(description = "Identificador único del producto", example = "1")
    private Integer id;

    @Schema(description = "Nombre del producto", example = "Harina de trigo")
    private String name;

    @Schema(description = "Tipo o categoría del producto", example = "Ingrediente")
    private String type;

    @Schema(description = "Unidad de medida del producto", example = "kg")
    private String unit;

    @Schema(description = "Precio unitario del producto", example = "1.50")
    private BigDecimal unitPrice;

    @Schema(description = "Código único del producto", example = "95082390574")
    private String productCode;

    @Schema(description = "Stock actual del producto", example = "250.00")
    private BigDecimal currentStock;

    @Schema(description = "Proveedor del producto")
    private SupplierResponseDTO supplier;
}
