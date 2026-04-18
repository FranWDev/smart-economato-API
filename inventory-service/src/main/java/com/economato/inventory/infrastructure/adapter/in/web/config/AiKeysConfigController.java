package com.economato.inventory.infrastructure.adapter.in.web.config;

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
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;

import com.economato.inventory.application.dto.request.GlobalApiKeyRequestDTO;
import com.economato.inventory.application.dto.response.GlobalApiKeyResponseDTO;
import com.economato.inventory.application.usecase.AiKeyVaultService;
import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

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
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;

    @GetMapping("/")
    @Operation(summary = "Listar API keys globales")
    public ResponseEntity<List<GlobalApiKeyResponseDTO>> list() {
        return ResponseEntity.ok(aiKeyVaultService.listGlobalKeys().stream().map(this::toDto).toList());
    }

    @PostMapping("/")
    @Operation(summary = "Crear API key global")
    public ResponseEntity<GlobalApiKeyResponseDTO> create(@Valid @RequestBody GlobalApiKeyRequestDTO request) {
        Integer adminUserId = currentAdminUserId();
        AiProvider provider = request.providerAsEnum();
        aiKeyVaultService.saveGlobalKey(provider, request.getApiKey(), adminUserId);
        return ResponseEntity.ok(findByProvider(provider));
    }

    @PutMapping("/")
    @Operation(summary = "Actualizar API key global")
    public ResponseEntity<GlobalApiKeyResponseDTO> update(@Valid @RequestBody GlobalApiKeyRequestDTO request) {
        Integer adminUserId = currentAdminUserId();
        AiProvider provider = request.providerAsEnum();
        aiKeyVaultService.updateGlobalKey(provider, request.getApiKey(), adminUserId);
        return ResponseEntity.ok(findByProvider(provider));
    }

    @DeleteMapping("/{provider}")
    @Operation(summary = "Eliminar API key global")
    public ResponseEntity<Void> deleteByProvider(@PathVariable String provider) {
        aiKeyVaultService.deleteGlobalKey(AiProvider.valueOf(provider.trim().toUpperCase()), currentAdminUserId());
        return ResponseEntity.noContent().build();
    }

    private GlobalApiKeyResponseDTO findByProvider(AiProvider provider) {
        return aiKeyVaultService.listGlobalKeys().stream()
                .filter(item -> item.provider() == provider)
                .findFirst()
                .map(this::toDto)
                .orElseThrow(() -> new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
    }

    private GlobalApiKeyResponseDTO toDto(AiKeyVaultService.ApiKeyMetadata metadata) {
        return GlobalApiKeyResponseDTO.builder()
                .provider(metadata.provider().name())
                .keyHint(metadata.keyHint())
                .active(metadata.active())
                .createdAt(metadata.createdAt())
                .build();
    }

    private Integer currentAdminUserId() {
        var currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_ADMIN_NOT_FOUND));
        }
        return currentUser.getId();
    }
}
