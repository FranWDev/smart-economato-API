package com.economato.inventory.infrastructure.adapter.in.web.ai.config;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.economato.inventory.application.dto.user.request.GlobalApiKeyRequestDTO;
import com.economato.inventory.application.dto.user.response.GlobalApiKeyResponseDTO;
import com.economato.inventory.application.usecase.ai.AiKeyVaultService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/config/ai-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Configuración de AI Keys", description = "Gestión de API keys globales por proveedor")
public class AiKeysConfigController {

    private final AiKeyVaultService aiKeyVaultService;

    @GetMapping("/")
    @Operation(summary = "Listar API keys globales")
    public ResponseEntity<List<GlobalApiKeyResponseDTO>> list() {
        return ResponseEntity.ok(aiKeyVaultService.listGlobalKeysDto());
    }

    @PostMapping("/")
    @Operation(summary = "Crear API key global")
    public ResponseEntity<GlobalApiKeyResponseDTO> create(@Valid @RequestBody GlobalApiKeyRequestDTO request) {
        return ResponseEntity.ok(aiKeyVaultService.saveGlobalKeyDto(request));
    }

    @PutMapping("/")
    @Operation(summary = "Actualizar API key global")
    public ResponseEntity<GlobalApiKeyResponseDTO> update(@Valid @RequestBody GlobalApiKeyRequestDTO request) {
        return ResponseEntity.ok(aiKeyVaultService.updateGlobalKeyDto(request));
    }

    @DeleteMapping("/{provider}")
    @Operation(summary = "Eliminar API key global")
    public ResponseEntity<Void> deleteByProvider(@PathVariable String provider) {
        return ResponseEntity.status(204).body(aiKeyVaultService.deleteGlobalKeyDto(provider));
    }
}
