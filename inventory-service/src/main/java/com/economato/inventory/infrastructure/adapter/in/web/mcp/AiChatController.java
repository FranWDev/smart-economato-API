package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.application.dto.mcp.McpApiKeyRequest;
import com.economato.inventory.application.dto.mcp.McpApiKeyResponseDto;
import com.economato.inventory.application.dto.mcp.McpChangeProviderRequest;
import com.economato.inventory.application.dto.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.mcp.McpChatMessageResponseDto;
import com.economato.inventory.application.dto.mcp.McpChatResponseDto;
import com.economato.inventory.application.usecase.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "MCP Chat", description = "Endpoints de chat AI para clientes MCP")
public class AiChatController {

    private final AiChatService aiChatService;

    @Operation(summary = "Listar chats activos", description = "Devuelve los chats activos del usuario autenticado")
    @GetMapping("/chats")
    public List<McpChatResponseDto> listChats() {
        return aiChatService.listChats();
    }

    @Operation(summary = "Crear chat", description = "Crea un nuevo chat AI para el usuario")
    @PostMapping("/chats")
    public McpChatResponseDto createChat(@Valid @RequestBody(required = false) McpChatCreateRequest request) {
        return aiChatService.createChat(request);
    }

    @Operation(summary = "Listar historial de chat", description = "Obtiene todos los mensajes de un chat del usuario")
    @GetMapping("/chats/{chatId}/messages")
    public List<McpChatMessageResponseDto> getChatHistory(@PathVariable Long chatId) {
        return aiChatService.getChatHistory(chatId);
    }

    @Operation(summary = "Enviar mensaje en streaming", description = "Envía un mensaje y devuelve la respuesta AI como SSE")
    @PostMapping(value = "/chats/{chatId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(26
            @PathVariable Long chatId,
            @Valid @RequestBody McpChatMessageRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return aiChatService.sendMessage(chatId, request, extractJwt(authorizationHeader));
    }

    @Operation(summary = "Cambiar proveedor del chat", description = "Actualiza el proveedor AI activo en el chat")
    @PatchMapping("/chats/{chatId}/provider")
    public McpChatResponseDto changeProvider(@PathVariable Long chatId,
                                             @Valid @RequestBody McpChangeProviderRequest request) {
        return aiChatService.changeProvider(chatId, request);
    }

    @Operation(summary = "Archivar chat", description = "Archiva un chat y lo excluye de la lista activa")
    @DeleteMapping("/chats/{chatId}")
    public void archiveChat(@PathVariable Long chatId) {
        aiChatService.archiveChat(chatId);
    }

    @Operation(summary = "Listar API keys", description = "Devuelve las API keys del usuario para proveedores AI")
    @GetMapping("/keys")
    public List<McpApiKeyResponseDto> listApiKeys() {
        return aiChatService.listApiKeys();
    }

    @Operation(summary = "Crear API key", description = "Guarda una nueva API key para un proveedor")
    @PostMapping("/keys")
    public McpApiKeyResponseDto createApiKey(@Valid @RequestBody McpApiKeyRequest request) {
        return aiChatService.saveApiKey(request);
    }

    @Operation(summary = "Actualizar API key", description = "Actualiza la API key de un proveedor")
    @PutMapping("/keys")
    public McpApiKeyResponseDto updateApiKey(@Valid @RequestBody McpApiKeyRequest request) {
        return aiChatService.updateApiKey(request);
    }

    @Operation(summary = "Eliminar API key", description = "Elimina una API key por identificador")
    @DeleteMapping("/keys/{keyId}")
    public void deleteApiKey(@PathVariable Long keyId) {
        aiChatService.deleteApiKey(keyId);
    }

    @Operation(summary = "Listar proveedores habilitados", description = "Devuelve proveedores AI activos y su modelo por defecto")
    @GetMapping("/providers")
    public List<Map<String, String>> listEnabledProviders() {
        return aiChatService.listEnabledProviders();
    }

    private String extractJwt(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return "";
        }
        String lower = authHeader.toLowerCase();
        if (lower.startsWith("bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader.trim();
    }
}
