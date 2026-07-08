package com.economato.inventory.application.dto.product.request;

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


    @NotBlank(message = "{validation.productRequestDTO.unit.notBlank}")
    @Size(max = 20, message = "{validation.productRequestDTO.unit.size}")
    @Schema(description = "Unidad de medida del producto (KG, G, L, ML, CUCHARADA, TAZA, UNIDAD, BOTE, BOLSA, CAJA, etc.)", example = "KG")
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

    @DecimalMin(value = "0.001", message = "{validation.productRequestDTO.lotQuantity.decimalMin}")
    @Digits(integer = 10, fraction = 3, message = "{validation.productRequestDTO.lotQuantity.digits}")
    @Schema(description = "Cantidad por lote de compra. Representa la unidad mínima de compra del producto (ej: 1.000 para botes de 1L, 5.000 para sacos de 5kg). Si se establece, el frontend puede redondear las cantidades de pedido al múltiplo superior de este valor.", example = "1.000")
    private BigDecimal lotQuantity;


    @Schema(description = "ID del proveedor del producto", example = "1")
    private Integer supplierId;

    @Schema(description = "ID del lote al que aplicar la modificación de stock. Si se omite, se aplica FEFO para salidas o se crea lote nuevo para entradas.", example = "12")
    private Long batchId;

    @Schema(description = "Fecha de expiración para el stock inicial", example = "2026-12-31")
    private java.time.LocalDate expirationDate;
}
