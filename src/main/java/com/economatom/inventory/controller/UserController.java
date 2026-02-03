package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.UserRequestDTO;
import com.economatom.inventory.dto.response.UserResponseDTO;
import com.economatom.inventory.service.UserService;
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

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuarios", description = "Operaciones relacionadas con los usuarios")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener todos los usuarios", description = "Devuelve una lista paginada de todos los usuarios. Solo accesible para administradores.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<List<UserResponseDTO>> getAll(Pageable pageable) {
        List<UserResponseDTO> users = service.findAll(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @Operation(summary = "Obtener usuario por ID", description = "Devuelve los datos de un usuario específico. Accesible para administradores o para el propio usuario.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario encontrado",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponseDTO.class))),
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
    @Operation(summary = "Crear usuario", description = "Crea un nuevo usuario. Solo accesible para administradores.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario creado",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<UserResponseDTO> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Datos del usuario a crear",
                required = true,
                content = @Content(schema = @Schema(implementation = UserRequestDTO.class))
            )
            @Valid @RequestBody UserRequestDTO userRequest) {
        UserResponseDTO createdUser = service.save(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @Operation(summary = "Actualizar usuario", description = "Actualiza los datos de un usuario. Accesible para administradores o para el propio usuario.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario actualizado",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UserResponseDTO> update(
            @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Datos del usuario a actualizar",
                required = true,
                content = @Content(schema = @Schema(implementation = UserRequestDTO.class))
            )
            @Valid @RequestBody UserRequestDTO userRequest) {
        return service.update(id, userRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @Operation(summary = "Eliminar usuario", description = "Elimina un usuario por ID. Accesible para administradores o para el propio usuario.")
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
}
