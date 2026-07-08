package com.economato.inventory.infrastructure.adapter.in.web.notification;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;

import com.economato.inventory.application.dto.notification.request.SendNotificationRequestDTO;
import com.economato.inventory.application.dto.notification.response.NotificationResponseDTO;
import com.economato.inventory.application.dto.notification.response.NotificationUnreadCountDTO;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.domain.model.notification.NotificationType;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Gestión de notificaciones persistidas y envío manual. [Rol requerido según endpoint]")
public class NotificationController {

    private final PersistentNotificationService persistentNotificationService;
    private final UserRepository userRepository;
    private final I18nService i18nService;

    @PostMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar notificación a un rol", description = "Persistencia + push WebSocket para todos los usuarios visibles del rol indicado.")
    @ApiResponse(responseCode = "200", description = "Notificación enviada correctamente")
    public ResponseEntity<Void> sendToRole(
            @PathVariable Role role,
            @RequestParam String title,
            @RequestParam String message) {
        persistentNotificationService.sendManualNotification(new SendNotificationRequestDTO(title, message, null, role));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar notificación a un usuario", description = "Persistencia + push WebSocket para un usuario visible específico.")
    @ApiResponse(responseCode = "200", description = "Notificación enviada correctamente")
    public ResponseEntity<Void> sendToUser(
            @PathVariable String username,
            @RequestParam String title,
            @RequestParam String message) {
        Integer recipientId = userRepository.findByName(username)
                .or(() -> userRepository.findByUser(username))
                .map(user -> user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND,
                        new Object[] {username})));

        persistentNotificationService.sendManualNotification(
                new SendNotificationRequestDTO(title, message, List.of(recipientId), null));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar notificación manual", description = "Envía una notificación manual persistida y push WebSocket a destinatarios por ids, rol o todos.")
    @ApiResponse(responseCode = "200", description = "Notificación enviada correctamente")
    public ResponseEntity<Void> sendManualNotification(@Valid @RequestBody SendNotificationRequestDTO request) {
        persistentNotificationService.sendManualNotification(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener mis notificaciones", description = "Retorna notificaciones del usuario autenticado con filtros opcionales y paginación.")
    @ApiResponse(responseCode = "200", description = "Listado paginado obtenido", content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationResponseDTO.class)))
    public ResponseEntity<Page<NotificationResponseDTO>> getMyNotifications(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        return ResponseEntity.ok(persistentNotificationService.getMyNotifications(type, isRead, from, to, pageable));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener contador de no leídas", description = "Retorna la cantidad de notificaciones no leídas del usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Contador obtenido correctamente")
    public ResponseEntity<NotificationUnreadCountDTO> getUnreadCount() {
        return ResponseEntity.ok(persistentNotificationService.getUnreadCount());
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marcar como leída", description = "Marca como leída una notificación propia.")
    @ApiResponse(responseCode = "204", description = "Notificación marcada como leída")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        persistentNotificationService.markAsRead(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marcar todas como leídas", description = "Marca como leídas todas las notificaciones propias.")
    @ApiResponse(responseCode = "204", description = "Notificaciones marcadas como leídas")
    public ResponseEntity<Void> markAllAsRead() {
        persistentNotificationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Eliminar notificación", description = "Realiza soft-delete de una notificación propia.")
    @ApiResponse(responseCode = "204", description = "Notificación eliminada")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        persistentNotificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/group/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar envío manual por grupo", description = "Marca como eliminadas por emisor todas las notificaciones manuales del grupo generado en un push masivo.")
    @ApiResponse(responseCode = "204", description = "Grupo eliminado")
    public ResponseEntity<Void> deleteManualNotificationGroup(@PathVariable String groupId) {
        persistentNotificationService.deleteManualNotificationGroup(groupId);
        return ResponseEntity.noContent().build();
    }
}
