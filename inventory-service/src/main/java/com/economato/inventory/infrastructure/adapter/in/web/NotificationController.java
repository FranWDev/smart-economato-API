package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.usecase.RoleNotificationService;
import com.economato.inventory.domain.model.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Envío de notificaciones manuales a través de WebSockets por rol o usuario. [Rol requerido: ADMIN]")
public class NotificationController {

    private final RoleNotificationService roleNotificationService;

    @PostMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar notificación a un rol", description = "Envía una notificación push vía WebSocket a todos los usuarios que tengan el rol especificado.")
    public ResponseEntity<Void> sendToRole(
            @PathVariable Role role,
            @RequestParam String title,
            @RequestParam String message) {
        roleNotificationService.sendNotificationToRole(role, title, message);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar notificación a un usuario", description = "Envía una notificación push vía WebSocket a un usuario específico.")
    public ResponseEntity<Void> sendToUser(
            @PathVariable String username,
            @RequestParam String title,
            @RequestParam String message) {
        roleNotificationService.sendNotificationToUser(username, title, message);
        return ResponseEntity.ok().build();
    }
}
