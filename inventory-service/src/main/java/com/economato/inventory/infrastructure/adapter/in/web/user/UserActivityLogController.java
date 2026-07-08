package com.economato.inventory.infrastructure.adapter.in.web.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.economato.inventory.application.dto.user.response.UserActivityLogResponseDTO;
import com.economato.inventory.application.usecase.user.UserActivityLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user-activity")
@RequiredArgsConstructor
@Tag(name = "Historial de Actividad de Usuarios", description = "Consulta del historial de actividad y presencia de usuarios")
public class UserActivityLogController {

    private final UserActivityLogService activityLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener todo el historial de actividad", description = "Devuelve una lista paginada de toda la actividad de usuarios. Ordenado por timestamp descendente para scroll loading. [Rol requerido: ADMIN]")
    public ResponseEntity<Page<UserActivityLogResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(activityLogService.getAllActivity(pageable));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    @Operation(summary = "Obtener historial de actividad de un usuario", description = "Devuelve el historial paginado de un usuario específico. ADMIN puede ver cualquier usuario. CHEF solo puede ver sus alumnos. [Rol requerido: ADMIN o CHEF]")
    public ResponseEntity<Page<UserActivityLogResponseDTO>> getByUserId(
            @PathVariable Integer userId,
            Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(activityLogService.getActivityByUserId(userId, authentication.getName(), pageable));
    }

    @GetMapping("/my-students")
    @PreAuthorize("hasRole('CHEF')")
    @Operation(summary = "Obtener historial de actividad de mis alumnos", description = "Devuelve el historial paginado de todos los alumnos del chef autenticado. [Rol requerido: CHEF]")
    public ResponseEntity<Page<UserActivityLogResponseDTO>> getMyStudentsActivity(
            Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(activityLogService.getMyStudentsActivity(authentication.getName(), pageable));
    }
}
