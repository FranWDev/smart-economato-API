package com.economato.user.infrastructure.adapter.in.web;

import com.economato.user.application.dto.request.BatchTeacherAssignmentRequestDTO;
import com.economato.user.application.dto.request.ChangePasswordRequestDTO;
import com.economato.user.application.dto.request.RoleEscalationRequestDTO;
import com.economato.user.application.dto.request.UserRequestDTO;
import com.economato.user.application.dto.response.BatchTeacherAssignmentResponseDTO;
import com.economato.user.application.dto.response.UserResponseDTO;
import com.economato.user.application.dto.response.UserStatsResponseDTO;
import com.economato.user.application.port.in.UserManagementUseCase;
import com.economato.user.domain.model.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/users", "/api/users"})
@Tag(name = "Usuarios", description = "Endpoints para la gestión de usuarios")
public class UserController {

    private final UserManagementUseCase userManagementUseCase;

    public UserController(UserManagementUseCase userManagementUseCase) {
        this.userManagementUseCase = userManagementUseCase;
    }

    @Operation(summary = "Obtener lista de usuarios visibles")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    @GetMapping
    public ResponseEntity<Page<UserResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(userManagementUseCase.findAll(pageable));
    }

    @Operation(summary = "Obtener usuario por ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'USER')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getById(@PathVariable Integer id) {
        return userManagementUseCase.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Obtener usuario actual (perfil)")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMe(Authentication authentication) {
        return ResponseEntity.ok(userManagementUseCase.findCurrentUser(authentication.getName()));
    }

    @Operation(summary = "Crear un nuevo usuario")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Validated(UserRequestDTO.OnCreate.class) @RequestBody UserRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementUseCase.save(requestDTO));
    }

    @Operation(summary = "Actualizar un usuario existente")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Integer id,
                                                   @Validated(UserRequestDTO.OnUpdate.class) @RequestBody UserRequestDTO requestDTO) {
        return userManagementUseCase.update(id, requestDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Eliminar un usuario por ID")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        userManagementUseCase.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cambiar contraseña")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/change-password")
    public ResponseEntity<Void> changePassword(@PathVariable Integer id,
                                               @Valid @RequestBody ChangePasswordRequestDTO request,
                                               Authentication authentication) {
        userManagementUseCase.changePasswordByActor(id, request, authentication);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Asignar profesor en batch a varios alumnos")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign-teacher-batch")
    public ResponseEntity<BatchTeacherAssignmentResponseDTO> assignTeacherBatch(@Valid @RequestBody BatchTeacherAssignmentRequestDTO request) {
        return ResponseEntity.ok(userManagementUseCase.assignTeacherBatch(request));
    }

    @Operation(summary = "Obtener estadísticas globales de usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponseDTO> getStats() {
        return ResponseEntity.ok(userManagementUseCase.getUserStats());
    }

    @Operation(summary = "Obtener usuarios por rol")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    @GetMapping("/role/{role}")
    public ResponseEntity<List<UserResponseDTO>> getByRole(@PathVariable Role role) {
        return ResponseEntity.ok(userManagementUseCase.findByRole(role));
    }

    @Operation(summary = "Obtener lista de profesores (CHEF)")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    @GetMapping("/teachers")
    public ResponseEntity<List<UserResponseDTO>> getTeachers() {
        return ResponseEntity.ok(userManagementUseCase.findByRole(Role.CHEF));
    }
}
