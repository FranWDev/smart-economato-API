package com.economato.user.application.service;

import com.economato.user.application.dto.event.UserCreatedEvent;
import com.economato.user.application.dto.event.UserDeletedEvent;
import com.economato.user.application.dto.event.UserRoleChangedEvent;
import com.economato.user.application.dto.event.UserUpdatedEvent;
import com.economato.user.application.dto.request.BatchTeacherAssignmentRequestDTO;
import com.economato.user.application.dto.request.ChangePasswordRequestDTO;
import com.economato.user.application.dto.request.RoleEscalationRequestDTO;
import com.economato.user.application.dto.request.UserRequestDTO;
import com.economato.user.application.dto.response.BatchTeacherAssignmentResponseDTO;
import com.economato.user.application.dto.response.UserResponseDTO;
import com.economato.user.application.dto.response.UserStatsResponseDTO;
import com.economato.user.application.dto.response.UserSummaryDTO;
import com.economato.user.application.port.in.UserManagementUseCase;
import com.economato.user.application.port.out.UserEventPublisherPort;
import com.economato.user.application.port.out.UserRepositoryPort;
import com.economato.user.domain.model.Role;
import com.economato.user.domain.model.User;
import com.economato.user.infrastructure.config.security.JwtUtils;
import com.economato.user.infrastructure.config.web.I18nService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements UserManagementUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final UserAccessPolicy accessPolicy;
    private final PasswordEncoder passwordEncoder;
    private final I18nService i18nService;
    private final JwtUtils jwtUtils;
    private final UserEventPublisherPort eventPublisher;

    public UserService(UserRepositoryPort userRepositoryPort,
                       UserAccessPolicy accessPolicy,
                       PasswordEncoder passwordEncoder,
                       I18nService i18nService,
                       JwtUtils jwtUtils,
                       UserEventPublisherPort eventPublisher) {
        this.userRepositoryPort = userRepositoryPort;
        this.accessPolicy = accessPolicy;
        this.passwordEncoder = passwordEncoder;
        this.i18nService = i18nService;
        this.jwtUtils = jwtUtils;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Page<UserResponseDTO> findAll(Pageable pageable) {
        return userRepositoryPort.findByIsHidden(false, pageable).map(this::toResponseDTO);
    }

    @Override
    public Page<UserResponseDTO> searchVisibleUsers(String term, Pageable pageable) {
        return userRepositoryPort.findByIsHidden(false, pageable).map(this::toResponseDTO);
    }

    @Override
    public Page<UserResponseDTO> searchVisibleTeachers(String term, Pageable pageable) {
        return userRepositoryPort.findByRoleIn(List.of(Role.CHEF, Role.ADMIN), pageable).map(this::toResponseDTO);
    }

    @Override
    public Optional<UserResponseDTO> findById(Integer id) {
        return userRepositoryPort.findById(id).map(this::toResponseDTO);
    }

    @Override
    public User findByUsername(String username) {
        return userRepositoryPort.findByName(username)
                .orElseGet(() -> userRepositoryPort.findByUser(username)
                        .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username)));
    }

    @Override
    public UserResponseDTO findCurrentUser(String username) {
        User user = findByUsername(username);
        return toResponseDTO(user);
    }

    @Override
    public UserResponseDTO findCurrentUserWithToken(String username, String token) {
        UserResponseDTO dto = findCurrentUser(username);
        dto.setToken(token);
        return dto;
    }

    @Override
    @Transactional
    public UserResponseDTO save(UserRequestDTO requestDTO) {
        accessPolicy.validatePasswordLength(requestDTO.getPassword());
        if (userRepositoryPort.existsByUser(requestDTO.getUser())) {
            throw new RuntimeException("El nombre de usuario ya está registrado");
        }

        User user = new User();
        user.setName(requestDTO.getName());
        user.setUser(requestDTO.getUser());
        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        user.setRole(requestDTO.getRole() != null ? requestDTO.getRole() : Role.USER);
        user.setFirstLogin(true);
        user.setHidden(false);

        if (requestDTO.getTeacherId() != null) {
            accessPolicy.validateTeacherAssignment(user.getRole(), requestDTO.getTeacherId());
            User teacher = userRepositoryPort.findById(requestDTO.getTeacherId()).orElse(null);
            user.setTeacher(teacher);
        }

        User saved = userRepositoryPort.save(user);

        // Publish Kafka Event
        UserCreatedEvent createdEvent = new UserCreatedEvent(
                UUID.randomUUID().toString(),
                "UserCreated",
                saved.getId().toString(),
                "User",
                Instant.now(),
                "1.0",
                new UserCreatedEvent.UserCreatedPayload(
                        saved.getId(),
                        saved.getName(),
                        saved.getUser(),
                        saved.getRole().name(),
                        saved.getTeacher() != null ? saved.getTeacher().getId() : null
                )
        );
        eventPublisher.publishUserCreated(createdEvent);

        return toResponseDTO(saved);
    }

    @Override
    @Transactional
    public Optional<UserResponseDTO> update(Integer id, UserRequestDTO requestDTO) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Role oldRole = user.getRole();
        user.setName(requestDTO.getName());
        if (requestDTO.getRole() != null) {
            user.setRole(requestDTO.getRole());
        }

        if (requestDTO.getPassword() != null && !requestDTO.getPassword().isBlank()) {
            accessPolicy.validatePasswordLength(requestDTO.getPassword());
            user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        }

        if (requestDTO.getTeacherId() != null) {
            accessPolicy.validateTeacherAssignment(user.getRole(), requestDTO.getTeacherId());
            User teacher = userRepositoryPort.findById(requestDTO.getTeacherId()).orElse(null);
            user.setTeacher(teacher);
        }

        User updated = userRepositoryPort.save(user);

        // Publish UserUpdatedEvent
        UserUpdatedEvent updatedEvent = new UserUpdatedEvent(
                UUID.randomUUID().toString(),
                "UserUpdated",
                updated.getId().toString(),
                "User",
                Instant.now(),
                "1.0",
                new UserUpdatedEvent.UserUpdatedPayload(
                        updated.getId(),
                        updated.getName(),
                        updated.getUser(),
                        updated.getRole().name(),
                        updated.getTeacher() != null ? updated.getTeacher().getId() : null
                )
        );
        eventPublisher.publishUserUpdated(updatedEvent);

        if (requestDTO.getRole() != null && !oldRole.equals(requestDTO.getRole())) {
            UserRoleChangedEvent roleEvent = new UserRoleChangedEvent(
                    UUID.randomUUID().toString(),
                    "UserRoleChanged",
                    updated.getId().toString(),
                    "User",
                    Instant.now(),
                    "1.0",
                    new UserRoleChangedEvent.UserRoleChangedPayload(
                            updated.getId(),
                            oldRole.name(),
                            updated.getRole().name()
                    )
            );
            eventPublisher.publishUserRoleChanged(roleEvent);
        }

        return Optional.of(toResponseDTO(updated));
    }

    @Override
    @Transactional
    public void deleteById(Integer id) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        accessPolicy.validateAdminDeletion(user);

        userRepositoryPort.deleteById(id);

        UserDeletedEvent deletedEvent = new UserDeletedEvent(
                UUID.randomUUID().toString(),
                "UserDeleted",
                id.toString(),
                "User",
                Instant.now(),
                "1.0",
                new UserDeletedEvent.UserDeletedPayload(id, user.getUser())
        );
        eventPublisher.publishUserDeleted(deletedEvent);
    }

    @Override
    public void updateFirstLoginStatus(Integer id, boolean status, boolean isAdmin) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        accessPolicy.validateFirstLoginReactivation(user, status, isAdmin);
        user.setFirstLogin(status);
        userRepositoryPort.save(user);
    }

    @Override
    public void updateFirstLoginStatusByActor(Integer id, boolean status, Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        updateFirstLoginStatus(id, status, isAdmin);
    }

    @Override
    public void changePassword(Integer id, ChangePasswordRequestDTO request) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        accessPolicy.validatePasswordChange(user, request, false);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFirstLogin(false);
        userRepositoryPort.save(user);
    }

    @Override
    public void changePasswordByActor(Integer id, ChangePasswordRequestDTO request, Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        accessPolicy.validatePasswordChange(user, request, isAdmin);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFirstLogin(false);
        userRepositoryPort.save(user);
    }

    @Override
    public List<UserResponseDTO> findByRole(Role role) {
        return userRepositoryPort.findByRole(role).stream().map(this::toResponseDTO).toList();
    }

    @Override
    public Page<UserResponseDTO> findHiddenUsers(Pageable pageable) {
        return userRepositoryPort.findByIsHidden(true, pageable).map(this::toResponseDTO);
    }

    @Override
    public void toggleUserHiddenStatus(Integer id, boolean hidden) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        accessPolicy.validateAdminHiding(user, hidden);
        user.setHidden(hidden);
        userRepositoryPort.save(user);
    }

    @Override
    public void assignTeacher(Integer userId, Integer teacherId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        accessPolicy.validateTeacherAssignment(user.getRole(), teacherId);
        User teacher = teacherId != null ? userRepositoryPort.findById(teacherId).orElse(null) : null;
        user.setTeacher(teacher);
        userRepositoryPort.save(user);
    }

    @Override
    public BatchTeacherAssignmentResponseDTO assignTeacherBatch(BatchTeacherAssignmentRequestDTO request) {
        List<Integer> failed = new ArrayList<>();
        int processed = 0;
        for (Integer studentId : request.getStudentIds()) {
            try {
                assignTeacher(studentId, request.getTeacherId());
                processed++;
            } catch (Exception e) {
                failed.add(studentId);
            }
        }
        return BatchTeacherAssignmentResponseDTO.builder()
                .success(failed.isEmpty())
                .processedCount(processed)
                .totalCount(request.getStudentIds().size())
                .failedStudentIds(failed)
                .message(failed.isEmpty() ? "Asignación completada" : "Algunos alumnos no pudieron ser asignados")
                .build();
    }

    @Override
    public List<UserResponseDTO> getMyStudents(String username) {
        User teacher = findByUsername(username);
        return userRepositoryPort.findByTeacherId(teacher.getId()).stream().map(this::toResponseDTO).toList();
    }

    @Override
    public List<UserResponseDTO> getStudentsByTeacherId(Integer teacherId) {
        return userRepositoryPort.findByTeacherId(teacherId).stream().map(this::toResponseDTO).toList();
    }

    @Override
    public UserStatsResponseDTO getUserStats() {
        Map<String, Long> roleStats = new HashMap<>();
        for (Role role : Role.values()) {
            roleStats.put(role.name(), userRepositoryPort.countByRole(role));
        }
        return UserStatsResponseDTO.builder()
                .totalUsers(userRepositoryPort.countByIsHidden(false))
                .usersByRole(roleStats)
                .build();
    }

    @Override
    public void escalateRole(Integer userId, RoleEscalationRequestDTO request) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        accessPolicy.validateRoleEscalation(user, request);
        userRepositoryPort.save(user);
    }

    @Override
    public void deescalateRole(Integer userId) {
        deescalateRole(userId, "System deescalation");
    }

    @Override
    public void deescalateRole(Integer userId, String reason) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        userRepositoryPort.save(user);
    }

    private UserResponseDTO toResponseDTO(User user) {
        if (user == null) return null;
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setUser(user.getUser());
        dto.setFirstLogin(user.isFirstLogin());
        dto.setHidden(user.isHidden());
        dto.setRole(user.getRole());
        if (user.getTeacher() != null) {
            dto.setTeacher(new UserSummaryDTO(
                    user.getTeacher().getId(),
                    user.getTeacher().getName(),
                    user.getTeacher().getUser(),
                    user.getTeacher().getRole()
            ));
        }
        return dto;
    }
}
