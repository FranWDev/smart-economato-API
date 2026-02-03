package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.SupplierRequestDTO;
import com.economatom.inventory.dto.response.SupplierResponseDTO;
import com.economatom.inventory.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@Tag(name = "Proveedores", description = "Gestión de proveedores en el sistema, CRUD completo y búsqueda por nombre")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Operation(
        summary = "Obtener todos los proveedores",
        description = "Devuelve una lista paginada de todos los proveedores registrados en el sistema.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de proveedores obtenida correctamente",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SupplierResponseDTO.class)))
        }
    )
    @GetMapping
    public ResponseEntity<Page<SupplierResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(supplierService.findAll(pageable));
    }

    @Operation(
        summary = "Obtener proveedor por ID",
        description = "Devuelve un proveedor específico según su ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Proveedor encontrado",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SupplierResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Proveedor no encontrado")
        }
    )
    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponseDTO> getById(@PathVariable Integer id) {
        return supplierService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Crear nuevo proveedor",
        description = "Crea un proveedor y devuelve la entidad creada con su ID asignado.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Datos del proveedor a crear",
            required = true,
            content = @Content(schema = @Schema(implementation = SupplierRequestDTO.class))
        ),
        responses = {
            @ApiResponse(responseCode = "201", description = "Proveedor creado correctamente",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SupplierResponseDTO.class)))
        }
    )
    @PostMapping
    public ResponseEntity<SupplierResponseDTO> create(
            @Valid @org.springframework.web.bind.annotation.RequestBody SupplierRequestDTO supplierRequest) {
        SupplierResponseDTO created = supplierService.save(supplierRequest);
        return ResponseEntity.created(URI.create("/api/suppliers/" + created.getId()))
                .body(created);
    }

    @Operation(
        summary = "Actualizar proveedor",
        description = "Actualiza un proveedor existente según su ID.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Datos del proveedor a actualizar",
            required = true,
            content = @Content(schema = @Schema(implementation = SupplierRequestDTO.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Proveedor actualizado correctamente",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SupplierResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Proveedor no encontrado")
        }
    )
    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponseDTO> update(
            @PathVariable Integer id,
            @Valid @org.springframework.web.bind.annotation.RequestBody SupplierRequestDTO supplierRequest) {
        return supplierService.update(id, supplierRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Eliminar proveedor",
        description = "Elimina un proveedor por su ID.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Proveedor eliminado correctamente"),
            @ApiResponse(responseCode = "404", description = "Proveedor no encontrado"),
            @ApiResponse(responseCode = "400", description = "No se puede eliminar el proveedor porque tiene productos asociados")
        }
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return supplierService.findById(id)
                .map(existing -> {
                    supplierService.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Buscar proveedores por nombre",
        description = "Busca proveedores cuyo nombre contenga el texto especificado (no sensible a mayúsculas).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de proveedores que coinciden con la búsqueda",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SupplierResponseDTO.class)))
        }
    )
    @GetMapping("/search")
    public ResponseEntity<List<SupplierResponseDTO>> searchByName(@RequestParam String name) {
        return ResponseEntity.ok(supplierService.findByNameContaining(name));
    }
}
