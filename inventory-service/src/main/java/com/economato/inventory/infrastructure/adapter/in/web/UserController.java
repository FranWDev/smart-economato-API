package com.economato.inventory.infrastructure.adapter.in.web;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import com.economato.inventory.application.dto.request.BatchTeacherAssignmentRequestDTO;
import com.economato.inventory.application.dto.request.ChangePasswordRequestDTO;
import com.economato.inventory.application.dto.request.RoleEscalationRequestDTO;
import com.economato.inventory.application.dto.request.TeacherAssignmentRequestDTO;
import com.economato.inventory.application.dto.request.TransferStudentsRequestDTO;
import com.economato.inventory.application.dto.request.UserRequestDTO;
import com.economato.inventory.application.dto.response.BatchTeacherAssignmentResponseDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.application.usecase.UserService;
import com.economato.inventory.domain.model.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

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
                        Authentication authentication) {
                return ResponseEntity.ok(service.findCurrentUser(authentication.getName()));
        }

        @GetMapping
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Obtener todos los usuarios", description = "Devuelve una lista paginada de todos los usuarios. Accesible para administradores y chefs. [Rol requerido: ADMIN, CHEF]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de usuarios", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<Page<UserResponseDTO>> getAll(Pageable pageable) {
                Page<UserResponseDTO> users = service.findAll(pageable);
                return ResponseEntity.ok(users);
        }

        @GetMapping("/search")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Buscar usuarios", description = "Busca usuarios visibles por nombre o usuario con paginación. Accesible para administradores y chefs. [Rol requerido: ADMIN, CHEF]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Resultados de búsqueda", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<Page<UserResponseDTO>> searchUsers(
                        @Parameter(description = "Texto a buscar en nombre o usuario", required = true) @org.springframework.web.bind.annotation.RequestParam String term,
                        Pageable pageable) {
                Page<UserResponseDTO> users = service.searchVisibleUsers(term, pageable);
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
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos del usuario a crear (contraseña obligatoria)", required = true, content = @Content(schema = @Schema(implementation = UserRequestDTO.class))) @Validated(UserRequestDTO.OnCreate.class) @RequestBody UserRequestDTO userRequest) {
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
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos del usuario a actualizar (contraseña opcional; si se omite no se modifica)", required = true, content = @Content(schema = @Schema(implementation = UserRequestDTO.class))) @Validated(UserRequestDTO.OnUpdate.class) @RequestBody UserRequestDTO userRequest) {
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
                service.deleteById(id);
                return ResponseEntity.noContent().build();
        }

        @GetMapping("/by-role/{role}")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Obtener usuarios por rol", description = "Devuelve una lista de usuarios filtrados por rol. Accesible para administradores y chefs. [Rol requerido: ADMIN, CHEF]")
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
                        Authentication authentication) {

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
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos de cambio de contraseña", required = true) @RequestBody @Valid ChangePasswordRequestDTO request,
                        Authentication authentication) {

                boolean isAdmin = authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                boolean isSelf = id.equals(service.findByUsername(authentication.getName()).getId());

                service.changePassword(id, request, isAdmin, isSelf);
                return ResponseEntity.ok().build();
        }

        @GetMapping("/hidden")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Obtener usuarios ocultos", description = "Devuelve una lista paginada de todos los usuarios ocultos. Solo accesible para administradores. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de usuarios ocultos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<Page<UserResponseDTO>> getHiddenUsers(Pageable pageable) {
                Page<UserResponseDTO> hiddenUsers = service.findHiddenUsers(pageable);
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

        @GetMapping("/teachers")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Obtener profesores", description = "Devuelve una lista de todos los usuarios con rol CHEF que pueden ser asignados como profesores. [Rol requerido: Cualquiera autenticado]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de profesores", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "401", description = "No autenticado")
        })
        public ResponseEntity<List<UserResponseDTO>> getTeachers() {
                List<UserResponseDTO> teachers = service.findByRole(Role.CHEF);
                return ResponseEntity.ok(teachers);
        }

        @GetMapping("/teachers/search")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Buscar profesores", description = "Busca profesores (rol CHEF) por nombre o usuario usando coincidencia parcial y paginación. [Rol requerido: Cualquiera autenticado]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Resultados de búsqueda de profesores", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "401", description = "No autenticado")
        })
        public ResponseEntity<Page<UserResponseDTO>> searchTeachers(
                        @Parameter(description = "Texto a buscar (coincidencia parcial) en nombre o usuario") @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String term,
                        Pageable pageable) {
                Page<UserResponseDTO> teachers = service.searchVisibleTeachers(term, pageable);
                return ResponseEntity.ok(teachers);
        }

        @GetMapping("/teachers/{teacherId}/students")
        @PreAuthorize("hasRole('ADMIN') or (hasRole('CHEF') and #teacherId == @userService.findByUsername(authentication.name).id)")
        @Operation(summary = "Obtener alumnos de un profesor", description = "Devuelve los alumnos asignados a un profesor específico. [Rol requerido: ADMIN o CHEF (propio)]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de alumnos del profesor", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Profesor no encontrado")
        })
        public ResponseEntity<List<UserResponseDTO>> getStudentsByTeacher(
                        @Parameter(description = "ID del profesor", required = true) @PathVariable Integer teacherId) {
                return ResponseEntity.ok(service.getStudentsByTeacherId(teacherId));
        }

        @GetMapping("/students")
        @PreAuthorize("hasRole('CHEF')")
        @Operation(summary = "Obtener alumnos", description = "Devuelve los usuarios que tienen al profesor actual (CHEF) asignado. [Rol requerido: CHEF]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de alumnos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<List<UserResponseDTO>> getMyStudents(
                        Authentication authentication) {
                List<UserResponseDTO> students = service.getMyStudents(authentication.getName());
                return ResponseEntity.ok(students);
        }

        @PatchMapping("/{id}/teacher")
        @PreAuthorize("hasRole('ADMIN') or #id == @userService.findByUsername(authentication.name).id")
        @Operation(summary = "Asignar profesor", description = "Asigna o desasigna un profesor a un usuario específico. [Rol requerido: ADMIN o propietario de la cuenta]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Profesor asignado correctamente"),
                        @ApiResponse(responseCode = "400", description = "Operación inválida (ej. profesor no existe o usuario es CHEF)"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<Void> assignTeacher(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos de asignación de profesor", required = false) @RequestBody(required = false) TeacherAssignmentRequestDTO request) {
                Integer teacherId = request != null ? request.getTeacherId() : null;
                service.assignTeacher(id, teacherId);
                return ResponseEntity.ok().build();
        }

        @PatchMapping("/batch/teacher")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Asignar profesor en batch", description = "Asigna o desasigna un profesor a una lista de alumnos en una sola operación. La operación es atómica: si algún alumno no es válido, no se aplica ningún cambio. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Profesor asignado correctamente a todos los alumnos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BatchTeacherAssignmentResponseDTO.class))),
                        @ApiResponse(responseCode = "400", description = "Operación inválida (ej. alumno no encontrado, rol incorrecto)"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Alumno o profesor no encontrado")
        })
        public ResponseEntity<BatchTeacherAssignmentResponseDTO> assignTeacherBatch(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ID del profesor y lista de IDs de alumnos", required = true, content = @Content(schema = @Schema(implementation = BatchTeacherAssignmentRequestDTO.class))) @Valid @RequestBody BatchTeacherAssignmentRequestDTO request) {
                BatchTeacherAssignmentResponseDTO response = service.assignTeacherBatch(request);
                return ResponseEntity.ok(response);
        }

        @PostMapping("/{id}/escalate")
        @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
        @Operation(summary = "Escalar permisos temporalmente", description = "Eleva a un usuario al rol ELEVATED temporalmente. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Permisos escalados correctamente"),
                        @ApiResponse(responseCode = "400", description = "Operación inválida (usuario ya es ELEVATED o ADMIN)"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<Void> escalateRole(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Duración de los permisos en minutos", required = true) @RequestBody @Valid RoleEscalationRequestDTO request) {
                service.escalateRole(id, request);
                return ResponseEntity.ok().build();
        }

        @PostMapping("/{id}/de-escalate")
        @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
        @Operation(summary = "Revocar escalado de permisos temporal", description = "Elimina el rol ELEVATED temporal y devuelve al usuario a su rol base. [Rol requerido: ADMIN]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Permisos revocados"),
                        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        public ResponseEntity<Void> deescalateRole(
                        @Parameter(description = "ID del usuario", required = true) @PathVariable Integer id) {
                service.deescalateRole(id);
                return ResponseEntity.ok().build();
        }

        @Operation(summary = "Obtener alumnos sin profesor asignado", description = "Devuelve una lista de alumnos (USER o ELEVATED) que no están ocultos y no tienen profesor.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Lista de alumnos obtenida correctamente"),
                        @ApiResponse(responseCode = "403", description = "No tiene permisos para realizar esta acción")
        })
        @GetMapping("/students/unassigned")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<List<UserResponseDTO>> getStudentsUnassigned() {
                return ResponseEntity.ok(service.getStudentsWithoutTeacher());
        }

        @Operation(summary = "Ocultar/Mostrar todos los alumnos de un profesor", description = "Cambia el estado isHidden de todos los alumnos asignados a un profesor.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Estado de visibilidad actualizado correctamente"),
                        @ApiResponse(responseCode = "404", description = "Profesor no encontrado"),
                        @ApiResponse(responseCode = "400", description = "El usuario especificado no es un profesor")
        })
        @PatchMapping("/teachers/{teacherId}/students/hidden")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<BatchTeacherAssignmentResponseDTO> hideAllStudentsOfTeacher(
                        @Parameter(description = "ID del profesor", required = true) @PathVariable Integer teacherId,
                        @Parameter(description = "Nuevo estado de ocultación", required = true) @RequestBody boolean hidden) {
                return ResponseEntity.ok(service.hideAllStudentsOfTeacher(teacherId, hidden));
        }

        @Operation(summary = "Transferir alumnos de un profesor a otro (batch)", description = "Transfiere una lista específica de alumnos de un profesor origen a un profesor destino.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Transferencia completada"),
                        @ApiResponse(responseCode = "404", description = "Uno de los profesores no existe"),
                        @ApiResponse(responseCode = "400", description = "Operación inválida (mismo profesor, alumno no pertenece al origen, etc.)")
        })
        @PatchMapping("/batch/transfer-teacher")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<BatchTeacherAssignmentResponseDTO> transferStudents(
                        @Valid @RequestBody TransferStudentsRequestDTO request) {
                return ResponseEntity.ok(service.transferStudents(request));
        }

        @Operation(summary = "Transferir todos los alumnos de un profesor a otro", description = "Transfiere todos los alumnos asignados a un profesor origen hacia un profesor destino.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Transferencia total completada"),
                        @ApiResponse(responseCode = "404", description = "Uno de los profesores no existe"),
                        @ApiResponse(responseCode = "400", description = "Operación inválida (mismo profesor, etc.)")
        })
        @PatchMapping("/teachers/{fromTeacherId}/transfer-all/{toTeacherId}")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<BatchTeacherAssignmentResponseDTO> transferAllStudents(
                        @Parameter(description = "ID del profesor origen", required = true) @PathVariable Integer fromTeacherId,
                        @Parameter(description = "ID del profesor destino", required = true) @PathVariable Integer toTeacherId) {
                return ResponseEntity.ok(service.transferAllStudents(fromTeacherId, toTeacherId));
        }
}
