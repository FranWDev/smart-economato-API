package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.AllergenRequestDTO;
import com.economatom.inventory.dto.response.AllergenResponseDTO;
import com.economatom.inventory.service.AllergenService;
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

@RestController
@RequestMapping("/api/allergens")
@Tag(name = "Alérgenos", description = "Gestión de alérgenos en el sistema, CRUD completo y búsqueda por nombre")
public class AllergenController {

    private final AllergenService allergenService;

    public AllergenController(AllergenService allergenService) {
        this.allergenService = allergenService;
    }

    @Operation(
        summary = "Obtener todos los alérgenos",
        description = "Devuelve una lista paginada de todos los alérgenos registrados en el sistema.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de alérgenos obtenida correctamente",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AllergenResponseDTO.class)))
        }
    )
    @GetMapping
    public ResponseEntity<Page<AllergenResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(allergenService.findAll(pageable));
    }

    @Operation(
        summary = "Obtener alérgeno por ID",
        description = "Devuelve un alérgeno específico según su ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Alérgeno encontrado",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AllergenResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Alérgeno no encontrado")
        }
    )
    @GetMapping("/{id}")
    public ResponseEntity<AllergenResponseDTO> getById(@PathVariable Integer id) {
        return allergenService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Crear nuevo alérgeno",
        description = "Crea un alérgeno y devuelve la entidad creada con su ID asignado.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Datos del alérgeno a crear",
            required = true,
            content = @Content(schema = @Schema(implementation = AllergenRequestDTO.class))
        ),
        responses = {
            @ApiResponse(responseCode = "201", description = "Alérgeno creado correctamente",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AllergenResponseDTO.class)))
        }
    )
    @PostMapping
    public ResponseEntity<AllergenResponseDTO> create(
            @Valid @org.springframework.web.bind.annotation.RequestBody AllergenRequestDTO allergenRequest) {
        AllergenResponseDTO created = allergenService.save(allergenRequest);
        return ResponseEntity.created(URI.create("/api/allergens/" + created.getId()))
                .body(created);
    }

    @Operation(
        summary = "Actualizar alérgeno",
        description = "Actualiza un alérgeno existente según su ID.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Datos del alérgeno a actualizar",
            required = true,
            content = @Content(schema = @Schema(implementation = AllergenRequestDTO.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Alérgeno actualizado correctamente",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AllergenResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Alérgeno no encontrado")
        }
    )
    @PutMapping("/{id}")
    public ResponseEntity<AllergenResponseDTO> update(
            @PathVariable Integer id,
            @Valid @org.springframework.web.bind.annotation.RequestBody AllergenRequestDTO allergenRequest) {
        return allergenService.update(id, allergenRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Eliminar alérgeno",
        description = "Elimina un alérgeno existente según su ID.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Alérgeno eliminado correctamente"),
            @ApiResponse(responseCode = "404", description = "Alérgeno no encontrado")
        }
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable Integer id) {
        return allergenService.findById(id)
                .map(existing -> {
                    allergenService.deleteById(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Buscar alérgeno por nombre",
        description = "Devuelve un alérgeno que coincida exactamente con el nombre proporcionado.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Alérgeno encontrado",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AllergenResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Alérgeno no encontrado")
        }
    )
    @GetMapping("/search")
    public ResponseEntity<AllergenResponseDTO> searchByName(@RequestParam String name) {
        return allergenService.findByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
