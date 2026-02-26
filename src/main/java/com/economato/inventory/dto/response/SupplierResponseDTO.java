package com.economato.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de respuesta que representa un proveedor en el sistema.")
public class SupplierResponseDTO {

    @Schema(description = "ID único del proveedor", example = "1")
    private Integer id;

    @Schema(description = "Nombre del proveedor", example = "Distribuidora Nacional S.A.")
    private String name;

    @Schema(description = "Correo electrónico del proveedor", example = "contacto@distribuidora.com")
    private String email;

    @Schema(description = "Número de teléfono del proveedor", example = "+34611223344")
    private String phone;
}
