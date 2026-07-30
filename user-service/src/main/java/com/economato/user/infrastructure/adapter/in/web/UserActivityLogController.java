package com.economato.user.infrastructure.adapter.in.web;

import com.economato.user.application.dto.response.UserActivityLogResponseDTO;
import com.economato.user.application.port.in.UserActivityUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/activity", "/api/user-activity-logs", "/api/users/activity-log"})
@Tag(name = "Actividad de Usuarios", description = "Endpoints para registro y consulta de auditoría de usuarios")
public class UserActivityLogController {

    private final UserActivityUseCase userActivityUseCase;

    public UserActivityLogController(UserActivityUseCase userActivityUseCase) {
        this.userActivityUseCase = userActivityUseCase;
    }

    @Operation(summary = "Obtener todo el historial de actividad (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<UserActivityLogResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(userActivityUseCase.getAllActivity(pageable));
    }

    @Operation(summary = "Obtener historial de actividad de un usuario específico")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<UserActivityLogResponseDTO>> getByUserId(
            @PathVariable Integer userId,
            Authentication authentication,
            Pageable pageable) {
        return ResponseEntity.ok(userActivityUseCase.getActivityByUserId(userId, authentication.getName(), pageable));
    }
}
