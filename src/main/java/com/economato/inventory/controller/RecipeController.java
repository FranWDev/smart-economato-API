package com.economato.inventory.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.economato.inventory.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.dto.request.RecipeRequestDTO;
import com.economato.inventory.dto.response.RecipeResponseDTO;
import com.economato.inventory.service.RecipePdfService;
import com.economato.inventory.service.RecipeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recipes")
@Tag(name = "Recetas", description = "Operaciones relacionadas con las recetas")
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipePdfService recipePdfService;

    public RecipeController(RecipeService recipeService, RecipePdfService recipePdfService) {
        this.recipeService = recipeService;
        this.recipePdfService = recipePdfService;
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @GetMapping
    @Operation(summary = "Obtener todas las recetas", description = "Devuelve una lista paginada de todas las recetas. [Rol requerido: USER]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de recetas", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecipeResponseDTO.class)))
    })
    public ResponseEntity<Page<RecipeResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(recipeService.findAll(pageable));
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @GetMapping("/{id}")
    @Operation(summary = "Obtener receta por ID", description = "Devuelve los datos de una receta específica. [Rol requerido: USER]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receta encontrada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecipeResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<RecipeResponseDTO> getById(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer id) {
        return recipeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PostMapping
    @Operation(summary = "Crear receta", description = "Crea una nueva receta. [Rol requerido: CHEF]")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Receta creada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecipeResponseDTO.class)))
    })
    public ResponseEntity<RecipeResponseDTO> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos de la receta a crear", required = true, content = @Content(schema = @Schema(implementation = RecipeRequestDTO.class))) @Valid @RequestBody RecipeRequestDTO recipeRequest) {
        RecipeResponseDTO created = recipeService.save(recipeRequest);
        return ResponseEntity.status(201).body(created);
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar receta", description = "Actualiza los datos de una receta existente. " +
            "Este endpoint utiliza **bloqueo optimista** mediante control de versión para detectar " +
            "actualizaciones concurrentes. Si otro usuario modifica la receta simultáneamente, " +
            "se generará un error de concurrencia. [Rol requerido: CHEF]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receta actualizada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecipeResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Receta no encontrada"),
            @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia - La receta fue modificada por otro usuario")
    })
    public ResponseEntity<RecipeResponseDTO> update(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos de la receta a actualizar", required = true, content = @Content(schema = @Schema(implementation = RecipeRequestDTO.class))) @Valid @RequestBody RecipeRequestDTO recipeRequest) {
        return recipeService.update(id, recipeRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar receta", description = "Elimina una receta. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Receta eliminada"),
            @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<Object> delete(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer id) {
        return recipeService.findById(id)
                .map(existing -> {
                    recipeService.deleteById(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @GetMapping("/search")
    @Operation(summary = "Buscar recetas por nombre", description = "Devuelve todas las recetas cuyo nombre contenga la cadena indicada. [Rol requerido: USER]")
    public List<RecipeResponseDTO> searchByName(
            @Parameter(description = "Nombre a buscar", required = true) @RequestParam String name) {
        return recipeService.findByNameContaining(name);
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @GetMapping("/maxcost")
    @Operation(summary = "Buscar recetas por costo máximo", description = "Devuelve todas las recetas cuyo costo total sea menor al indicado. [Rol requerido: USER]")
    public List<RecipeResponseDTO> findByCostLessThan(
            @Parameter(description = "Costo máximo", required = true) @RequestParam BigDecimal maxCost) {
        return recipeService.findByCostLessThan(maxCost);
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PostMapping("/cook")
    @Operation(summary = "Cocinar receta", description = "Registra el cocinado de una receta, descontando automáticamente los ingredientes del inventario mediante el ledger inmutable. "
            +
            "Se registra una auditoría completa que incluye quién cocinó la receta, cuándo y qué cantidad. [Rol requerido: CHEF]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receta cocinada exitosamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecipeResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Stock insuficiente o datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<RecipeResponseDTO> cookRecipe(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos del cocinado de la receta", required = true, content = @Content(schema = @Schema(implementation = RecipeCookingRequestDTO.class))) @Valid @RequestBody RecipeCookingRequestDTO cookingRequest) {
        RecipeResponseDTO result = recipeService.cookRecipe(cookingRequest);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @GetMapping("/{id}/pdf")
    @Operation(summary = "Descargar receta en PDF", description = "Genera y descarga la receta en formato PDF con un diseño estético que incluye ingredientes, elaboración, alérgenos y coste total. [Rol requerido: USER]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generado correctamente", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "404", description = "Receta no encontrada"),
            @ApiResponse(responseCode = "500", description = "Error al generar el PDF")
    })
    public ResponseEntity<byte[]> downloadRecipePdf(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer id) {
        return recipeService.findById(id)
                .<ResponseEntity<byte[]>>map(recipe -> {
                    try {
                        byte[] pdfBytes = recipePdfService.generateRecipePdf(recipe);

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_PDF);
                        headers.setContentDisposition(ContentDisposition.attachment()
                                .filename(sanitizeFilename(recipe.getName()) + ".pdf")
                                .build());
                        headers.setContentLength(pdfBytes.length);

                        return ResponseEntity.ok()
                                .headers(headers)
                                .body(pdfBytes);
                    } catch (Exception e) {
                        return ResponseEntity.<byte[]>internalServerError().build();
                    }
                })
                .orElse(ResponseEntity.<byte[]>notFound().build());
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "receta";
        }
        // Eliminar caracteres no válidos para nombres de archivo
        String cleaned = filename.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s-]", "_")
                .replaceAll("\\s", "_");
        if (cleaned.isBlank()) {
            return "receta";
        }
        return cleaned.substring(0, Math.min(cleaned.length(), 50));
    }
}
