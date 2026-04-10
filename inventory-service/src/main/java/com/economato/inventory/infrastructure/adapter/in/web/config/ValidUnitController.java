package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.request.ValidUnitRequestDTO;
import com.economato.inventory.application.dto.response.ValidUnitResponseDTO;
import com.economato.inventory.application.usecase.ValidUnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config/units")
@RequiredArgsConstructor
@Tag(name = "Unidades de Medida", description = "Gestión de unidades válidas para productos")
public class ValidUnitController {

    private final ValidUnitService validUnitService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todas las unidades")
    public ResponseEntity<List<ValidUnitResponseDTO>> getAll() {
        return ResponseEntity.ok(validUnitService.getAll().stream().map(this::map).toList());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar unidades activas")
    public ResponseEntity<List<ValidUnitResponseDTO>> getActive() {
        return ResponseEntity.ok(validUnitService.getActive().stream().map(this::map).toList());
    }

    @PostMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear unidad")
    public ResponseEntity<ValidUnitResponseDTO> create(@Valid @RequestBody ValidUnitRequestDTO request) {
        return ResponseEntity.ok(map(validUnitService.create(request.getCode(), request.getCategory())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar unidad")
    public ResponseEntity<ValidUnitResponseDTO> update(@PathVariable Integer id,
                                                       @Valid @RequestBody ValidUnitRequestDTO request) {
        return ResponseEntity.ok(map(validUnitService.update(id, request.getCode(), request.getCategory(), request.getActive())));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar/desactivar unidad")
    public ResponseEntity<ValidUnitResponseDTO> toggle(@PathVariable Integer id) {
        return ResponseEntity.ok(map(validUnitService.toggleActive(id)));
    }

    private ValidUnitResponseDTO map(com.economato.inventory.domain.model.ValidUnit v) {
        return ValidUnitResponseDTO.builder()
                .id(v.getId())
                .code(v.getCode())
                .category(v.getCategory())
                .active(v.isActive())
                .build();
    }
}
