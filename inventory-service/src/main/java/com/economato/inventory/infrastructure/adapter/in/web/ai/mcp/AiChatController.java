package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.mcp.mcp.McpChangeProviderRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageResponseDto;
import com.economato.inventory.application.dto.mcp.mcp.McpChatResponseDto;
import com.economato.inventory.application.dto.mcp.mcp.McpChatUpdateRequest;
import com.economato.inventory.application.usecase.ai.AiChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "MCP Chat", description = "Endpoints de chat AI para clientes MCP")
public class AiChatController {

    private final AiChatService aiChatService;

    @Operation(summary = "Listar chats activos", description = "Devuelve los chats activos del usuario autenticado")
    @GetMapping("/chats")
    public ResponseEntity<List<McpChatResponseDto>> listChats() {
        return ResponseEntity.ok(aiChatService.listChats());
    }

    @Operation(summary = "Crear chat", description = "Crea un nuevo chat AI para el usuario")
    @PostMapping("/chats")
    public ResponseEntity<McpChatResponseDto> createChat(@Valid @RequestBody(required = false) McpChatCreateRequest request) {
        return ResponseEntity.ok(aiChatService.createChat(request));
    }

    @Operation(summary = "Listar historial de chat", description = "Obtiene todos los mensajes de un chat del usuario")
    @GetMapping("/chats/{chatId}/messages")
    public ResponseEntity<List<McpChatMessageResponseDto>> getChatHistory(@PathVariable Long chatId) {
        return ResponseEntity.ok(aiChatService.getChatHistory(chatId));
    }

    @Operation(summary = "Listar historial paginado", description = "Obtiene mensajes de un chat usando paginacion")
    @GetMapping("/chats/{chatId}/messages/page")
    public ResponseEntity<Page<McpChatMessageResponseDto>> getChatHistoryPage(@PathVariable Long chatId, Pageable pageable) {
        return ResponseEntity.ok(aiChatService.getChatHistory(chatId, pageable));
    }

    @Operation(summary = "Actualizar chat", description = "Permite actualizar el titulo de un chat")
    @PatchMapping("/chats/{chatId}")
    public ResponseEntity<McpChatResponseDto> updateChat(@PathVariable Long chatId,
                                         @Valid @RequestBody McpChatUpdateRequest request) {
        return ResponseEntity.ok(aiChatService.updateChat(chatId, request));
    }

    @Operation(summary = "Enviar mensaje en streaming", description = "Envía un mensaje y devuelve la respuesta AI como SSE")
    @PostMapping(value = "/chats/{chatId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> sendMessage(
            @PathVariable Long chatId,
            @Valid @RequestBody McpChatMessageRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return ResponseEntity.ok(aiChatService.sendMessage(chatId, request, authorizationHeader));
    }

    @Operation(summary = "Cambiar proveedor del chat", description = "Actualiza el proveedor AI activo en el chat")
    @PatchMapping("/chats/{chatId}/provider")
    public ResponseEntity<McpChatResponseDto> changeProvider(@PathVariable Long chatId,
                                             @Valid @RequestBody McpChangeProviderRequest request) {
        return ResponseEntity.ok(aiChatService.changeProvider(chatId, request));
    }

    @Operation(summary = "Archivar chat", description = "Archiva un chat y lo excluye de la lista activa")
    @DeleteMapping("/chats/{chatId}")
    public ResponseEntity<Void> archiveChat(@PathVariable Long chatId) {
        return ResponseEntity.ok(aiChatService.archiveChat(chatId));
    }

    @Operation(summary = "Listar proveedores habilitados", description = "Devuelve proveedores AI activos y su modelo por defecto")
    @GetMapping("/providers")
    public ResponseEntity<List<Map<String, String>>> listEnabledProviders() {
        return ResponseEntity.ok(aiChatService.listEnabledProviders());
    }
}
