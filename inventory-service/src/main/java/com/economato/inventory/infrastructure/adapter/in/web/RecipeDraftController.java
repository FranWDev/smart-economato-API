package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.RecipeDraftRejectRequestDTO;
import com.economato.inventory.application.dto.request.RecipeDraftRequestDTO;
import com.economato.inventory.application.dto.response.RecipeDraftResponseDTO;
import com.economato.inventory.application.usecase.RecipeDraftService;
import com.economato.inventory.domain.model.RecipeDraftStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipe-drafts")
@Tag(name = "Borradores de recetas", description = "Operaciones relacionadas con los borradores de recetas")
public class RecipeDraftController {

    private final RecipeDraftService recipeDraftService;

    @PreAuthorize("hasRole('USER')")
    @PostMapping
    @Operation(summary = "Crear borrador de receta")
    public ResponseEntity<RecipeDraftResponseDTO> create(@Valid @RequestBody RecipeDraftRequestDTO request) {
        return ResponseEntity.status(201).body(recipeDraftService.createDraft(request));
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar borrador de receta")
    public ResponseEntity<RecipeDraftResponseDTO> update(@PathVariable Integer id,
            @Valid @RequestBody RecipeDraftRequestDTO request) {
        return ResponseEntity.ok(recipeDraftService.updateDraft(id, request));
    }

    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar borrador de receta")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        recipeDraftService.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    @GetMapping
    @Operation(summary = "Listar borradores de recetas")
    public ResponseEntity<Page<RecipeDraftResponseDTO>> list(
            @RequestParam(value = "status", required = false) RecipeDraftStatus status,
            Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(recipeDraftService.findByStatus(status, pageable));
        }
        return ResponseEntity.ok(recipeDraftService.findAll(pageable));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/mine")
    @Operation(summary = "Listar mis borradores")
    public ResponseEntity<Page<RecipeDraftResponseDTO>> mine(Pageable pageable) {
        return ResponseEntity.ok(recipeDraftService.findMyDrafts(pageable));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de un borrador")
    public ResponseEntity<RecipeDraftResponseDTO> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(recipeDraftService.findById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/approve")
    @Operation(summary = "Aprobar borrador de receta")
    public ResponseEntity<RecipeDraftResponseDTO> approve(@PathVariable Integer id) {
        return ResponseEntity.ok(recipeDraftService.approveDraft(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/reject")
    @Operation(summary = "Rechazar borrador de receta")
    public ResponseEntity<RecipeDraftResponseDTO> reject(
            @PathVariable Integer id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = RecipeDraftRejectRequestDTO.class))) @Valid @RequestBody RecipeDraftRejectRequestDTO request) {
        return ResponseEntity.ok(recipeDraftService.rejectDraft(id, request));
    }
}