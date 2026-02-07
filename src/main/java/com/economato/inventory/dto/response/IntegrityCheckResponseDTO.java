package com.economato.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resultado de la verificación de integridad de la cadena de hashes")
public class IntegrityCheckResponseDTO {

    @Schema(description = "ID del producto verificado", example = "42")
    private Integer productId;

    @Schema(description = "Nombre del producto", example = "Harina de trigo")
    private String productName;

    @Schema(description = "¿La cadena es válida?", example = "true")
    private boolean valid;

    @Schema(description = "Mensaje del resultado", example = "Cadena íntegra: 10 transacciones verificadas")
    private String message;

    @Schema(description = "Lista de errores encontrados (si la cadena está corrupta)")
    private List<String> errors;

    @Schema(description = "Total de transacciones verificadas", example = "10")
    private int totalTransactions;
}
