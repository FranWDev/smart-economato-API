package com.economato.inventory.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.economato.inventory.dto.request.UserRequestDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.model.Role;
import com.economato.inventory.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuarios", description = "Operaciones relacionadas con los usuarios")
public class UserController {

        private final UserService service;

        public UserController(UserService service) {
                this.service = service;
        }

        @GetMapping("/me")
        @Operation(summary = "Obtener usuario actual", description = "Devuelve los datos del usuario autenticado en base a su token JWT. No requiere pasar ningún ID. [Rol requerido: cualquier usuario autenticado]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Datos del usuario actual", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "401", description = "No autenticado")
        })
        public ResponseEntity<UserResponseDTO> getCurrentUser(
                        org.springframework.security.core.Authentication authentication) {
                return ResponseEntity.ok(service.findCurrentUser(authentication.getName()));
        }

        @GetMapping
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Obtener todos los usuarios", description = "Devuelve una lista paginada de todos los usuarios. Solo accesible para administradores. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de usuarios", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<List<UserResponseDTO>> getAll(Pageable pageable) {
                List<UserResponseDTO> users = service.findAll(pageable);
                return ResponseEntity.ok(users);
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN') or #id == @userService.findByUsername(authentication.name).id")
        @Operation(summary = "Obtener usuario por ID", description = "Devuelve los datos de un usuario específico. Accesible para administradores o para el propio usuario. [Rol requerido: USER]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Usuario encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
        })
        public ResponseEntity<UserResponseDTO> getById(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id) {
                return service.findById(id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Crear usuario", description = "Crea un nuevo usuario. Solo accesible para administradores. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Usuario creado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<UserResponseDTO> create(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos del usuario a crear", required = true, content = @Content(schema = @Schema(implementation = UserRequestDTO.class))) @Valid @RequestBody UserRequestDTO userRequest) {
                UserResponseDTO createdUser = service.save(userRequest);
                return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Actualizar usuario", description = "Actualiza los datos de un usuario. Solo accesible para administradores. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Usuario actualizado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
        })
        public ResponseEntity<UserResponseDTO> update(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos del usuario a actualizar", required = true, content = @Content(schema = @Schema(implementation = UserRequestDTO.class))) @Valid @RequestBody UserRequestDTO userRequest) {
                return service.update(id, userRequest)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Eliminar usuario", description = "Elimina un usuario por ID. Solo accesible para administradores. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "204", description = "Usuario eliminado exitosamente"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
        })
        public ResponseEntity<Object> delete(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id) {
                return service.findById(id)
                                .map(existing -> {
                                        service.deleteById(id);
                                        return ResponseEntity.noContent().build();
                                })
                                .orElse(ResponseEntity.notFound().build());
        }

        @GetMapping("/by-role/{role}")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Obtener usuarios por rol", description = "Devuelve una lista de usuarios filtrados por rol. Solo accesible para administradores. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de usuarios con el rol especificado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<List<UserResponseDTO>> getByRole(
                        @Parameter(description = "Rol a filtrar", required = true) @PathVariable Role role) {
                List<UserResponseDTO> users = service.findByRole(role);
                return ResponseEntity.ok(users);
        }

        @PatchMapping("/{id}/first-login")
        @PreAuthorize("hasRole('ADMIN') or #id == @userService.findByUsername(authentication.name).id")
        @Operation(summary = "Actualizar estado de primer login", description = "Actualiza el estado de isFirstLogin. Los usuarios solo pueden cambiarlo a false (completar primer login), los administradores pueden cambiarlo a cualquier valor. [Rol requerido: USER]", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth"))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Estado actualizado correctamente"),
                        @ApiResponse(responseCode = "400", description = "Operación no permitida (usuario intenta reactivar primer login)"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                        @ApiResponse(responseCode = "401", description = "No autenticado")
        })
        public ResponseEntity<Void> updateFirstLoginStatus(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Nuevo estado (true/false)", required = true) @RequestBody boolean status,
                        org.springframework.security.core.Authentication authentication) {

                boolean isAdmin = authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                service.updateFirstLoginStatus(id, status, isAdmin);
                return ResponseEntity.ok().build();
        }

        @PatchMapping("/{id}/password")
        @PreAuthorize("hasRole('ADMIN') or #id == @userService.findByUsername(authentication.name).id")
        @Operation(summary = "Cambiar contraseña", description = "Permite cambiar la contraseña del usuario. Requiere contraseña actual si no es admin y el estado isFirstLogin (en la base de datos) es false. El estado isFirstLogin se valida desde la base de datos, no desde el request, para prevenir ataques. [Rol requerido: ADMIN o USER (propio)]", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth"))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Contraseña actualizada correctamente"),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos o contraseña actual incorrecta"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "401", description = "No autenticado")
        })
        public ResponseEntity<Void> changePassword(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos de cambio de contraseña", required = true) @RequestBody @Valid com.economato.inventory.dto.request.ChangePasswordRequestDTO request,
                        org.springframework.security.core.Authentication authentication) {

                boolean isAdmin = authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                service.changePassword(id, request, isAdmin);
                return ResponseEntity.ok().build();
        }

        @GetMapping("/hidden")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Obtener usuarios ocultos", description = "Devuelve una lista paginada de todos los usuarios ocultos. Solo accesible para administradores. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de usuarios ocultos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<List<UserResponseDTO>> getHiddenUsers(Pageable pageable) {
                List<UserResponseDTO> hiddenUsers = service.findHiddenUsers(pageable);
                return ResponseEntity.ok(hiddenUsers);
        }

        @PatchMapping("/{id}/hidden")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Cambiar estado oculto del usuario", description = "Oculta o muestra un usuario. Los usuarios ocultos no pueden hacer login ni aparecer en listados estándar. No se puede ocultar el último administrador visible. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Estado ocultamiento actualizado correctamente"),
                        @ApiResponse(responseCode = "400", description = "No se puede ocultar el último administrador visible"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<Void> toggleUserHidden(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "true para ocultar, false para mostrar", required = true) @RequestBody boolean hidden) {
                service.toggleUserHiddenStatus(id, hidden);
                return ResponseEntity.ok().build();
        }
}
