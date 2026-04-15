package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.IncidentTypeRequestDTO;
import com.economato.inventory.application.dto.response.IncidentTypeResponseDTO;
import com.economato.inventory.application.usecase.IncidentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incident-types")
@RequiredArgsConstructor
@Tag(name = "Tipos de incidencia", description = "Gestión de tipos de incidencia")
public class IncidentTypeController {

    private final IncidentTypeService incidentTypeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Listar tipos activos")
    public ResponseEntity<List<IncidentTypeResponseDTO>> getActive() {
        return ResponseEntity.ok(incidentTypeService.getActiveTypes());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todos los tipos (incluye inactivos)")
    public ResponseEntity<List<IncidentTypeResponseDTO>> getAll() {
        return ResponseEntity.ok(incidentTypeService.getAllTypes());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Obtener tipo por ID")
    public ResponseEntity<IncidentTypeResponseDTO> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(incidentTypeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear tipo")
    public ResponseEntity<IncidentTypeResponseDTO> create(@Valid @RequestBody IncidentTypeRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentTypeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar tipo")
    public ResponseEntity<IncidentTypeResponseDTO> update(@PathVariable Integer id,
                                                          @Valid @RequestBody IncidentTypeRequestDTO request) {
        return ResponseEntity.ok(incidentTypeService.update(id, request));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar o desactivar tipo")
    public ResponseEntity<IncidentTypeResponseDTO> toggleActive(@PathVariable Integer id) {
        return ResponseEntity.ok(incidentTypeService.toggleActive(id));
    }
}
