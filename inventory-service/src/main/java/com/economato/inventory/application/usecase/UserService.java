package com.economato.inventory.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.request.BatchTeacherAssignmentRequestDTO;
import com.economato.inventory.application.dto.request.ChangePasswordRequestDTO;
import com.economato.inventory.application.dto.request.RoleEscalationRequestDTO;
import com.economato.inventory.application.dto.request.UserRequestDTO;
import com.economato.inventory.application.dto.request.TransferStudentsRequestDTO;
import com.economato.inventory.application.dto.response.BatchTeacherAssignmentResponseDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.application.dto.response.UserStatsResponseDTO;
import com.economato.inventory.application.mapper.StatsMapper;
import com.economato.inventory.application.mapper.TemporaryRoleEscalationMapper;
import com.economato.inventory.application.mapper.UserMapper;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.TemporaryRoleEscalation;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.TemporaryRoleEscalationRepository;
import com.economato.inventory.application.dto.projection.UserProjection;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

@Service
@Transactional(rollbackFor = { RuntimeException.class, Exception.class })
public class UserService {
    private final I18nService i18nService;

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final StatsMapper statsMapper;
    private final TemporaryRoleEscalationMapper escalationMapper;
    private final CustomUserDetailsService customUserDetailsService;
    private final TemporaryRoleEscalationRepository escalationRepository;
    private final RoleNotificationService roleNotificationService;
    @Autowired(required = false)
    private SystemConfigService systemConfigService;

    public UserService(I18nService i18nService, UserRepository repository, PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            TemporaryRoleEscalationMapper escalationMapper,
            StatsMapper statsMapper,
            CustomUserDetailsService customUserDetailsService,
            TemporaryRoleEscalationRepository escalationRepository,
            RoleNotificationService roleNotificationService) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.escalationMapper = escalationMapper;
        this.statsMapper = statsMapper;
        this.customUserDetailsService = customUserDetailsService;
        this.escalationRepository = escalationRepository;
        this.roleNotificationService = roleNotificationService;
    }

        @Cacheable(value = "users_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
        @Transactional(readOnly = true)
    public Page<UserResponseDTO> findAll(Pageable pageable) {
        Page<UserResponseDTO> page = repository.findByIsHiddenFalse(pageable)
                .map(userMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

        @Transactional(readOnly = true)
        public Page<UserResponseDTO> searchVisibleUsers(String term, Pageable pageable) {
        String normalized = term == null ? "" : term.trim();

        Page<UserResponseDTO> page = normalized.isEmpty()
            ? repository.findByIsHiddenFalse(pageable).map(userMapper::toResponseDTO)
            : repository.searchVisibleByNameOrUser(normalized, pageable).map(userMapper::toResponseDTO);

        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
        }

    @Cacheable(value = "user", key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<UserResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(userMapper::toResponseDTO);
    }

    @Cacheable(value = "userByEmail", key = "#username")
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return repository.findByName(username)
                .orElseThrow(
                        () -> new UsernameNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findCurrentUser(String username) {
        return userMapper.toResponseDTO(findByUsername(username));
    }

        @Caching(evict = {
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "user_stats", allEntries = true),
            @CacheEvict(value = "users_by_role", allEntries = true),
            @CacheEvict(value = "users_no_teacher", allEntries = true)
        })
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public UserResponseDTO save(UserRequestDTO requestDTO) {

        if (repository.existsByUser(requestDTO.getUser())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_USER_ALREADY_EXISTS));
        }

        if (repository.findByName(requestDTO.getName()).isPresent()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_USER_ALREADY_EXISTS));
        }

        User user = userMapper.toEntity(requestDTO);
        validatePasswordLength(requestDTO.getPassword());
        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        user.setFirstLogin(true);

        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }

        validateTeacherAssignment(user.getRole(), requestDTO.getTeacherId());

        return userMapper.toResponseDTO(repository.save(user));
    }

        @Caching(evict = {
            @CacheEvict(value = "user", key = "#id"),
            @CacheEvict(value = "userByEmail", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "users_by_role", allEntries = true),
            @CacheEvict(value = "users_no_teacher", allEntries = true),
            @CacheEvict(value = "user_stats", allEntries = true)
        })
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<UserResponseDTO> update(Integer id, UserRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {

                    if (!existing.getUser().equals(requestDTO.getUser()) &&
                            repository.existsByUser(requestDTO.getUser())) {
                        throw new InvalidOperationException(
                                i18nService.getMessage(MessageKey.ERROR_AUTH_USER_ALREADY_EXISTS));
                    }

                    if (!existing.getName().equals(requestDTO.getName()) &&
                            repository.findByName(requestDTO.getName()).isPresent()) {
                        throw new InvalidOperationException(
                                i18nService.getMessage(MessageKey.ERROR_AUTH_USER_ALREADY_EXISTS));
                    }

                    userMapper.updateEntity(requestDTO, existing);

                    if (requestDTO.getPassword() != null && !requestDTO.getPassword().isEmpty()) {
                        validatePasswordLength(requestDTO.getPassword());
                        existing.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
                    }

                    validateTeacherAssignment(existing.getRole(), requestDTO.getTeacherId());

                    User updatedUser = repository.save(existing);
                    customUserDetailsService.evictUser(updatedUser.getName());
                    customUserDetailsService.evictUser(updatedUser.getUser());
                    return userMapper.toResponseDTO(updatedUser);
                });
    }

        @Caching(evict = {
            @CacheEvict(value = "user", key = "#id"),
            @CacheEvict(value = "userByEmail", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "user_stats", allEntries = true),
            @CacheEvict(value = "users_by_role", allEntries = true),
            @CacheEvict(value = "users_no_teacher", allEntries = true)
        })
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {

        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { id })));

        if (Role.ADMIN.equals(user.getRole())) {
            long adminCount = repository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_DELETE_LAST_ADMIN));
            }
        }

        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());
        repository.delete(user);
    }

        @Caching(evict = {
            @CacheEvict(value = "user", key = "#id"),
            @CacheEvict(value = "userByEmail", allEntries = true)
        })
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void updateFirstLoginStatus(Integer id, boolean status, boolean isAdmin) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { id })));

        // Validación de seguridad: solo un admin puede cambiar firstLogin de false a
        // true
        // Un usuario normal solo puede marcarlo como false (completar primer login)
        if (!isAdmin && status && !user.isFirstLogin()) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_USER_FIRST_LOGIN_REACTIVATE_DENIED));
        }

        user.setFirstLogin(status);
        repository.save(user);
        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());
    }

        @Caching(evict = {
            @CacheEvict(value = "user", key = "#id"),
            @CacheEvict(value = "userByEmail", allEntries = true)
        })
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void changePassword(Integer id, ChangePasswordRequestDTO request,
            boolean isAdmin, boolean isSelf) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { id })));

        validatePasswordLength(request.getNewPassword());
        boolean wasFirstLogin = user.isFirstLogin();

        if (isAdmin) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            if (wasFirstLogin && isSelf) {
                user.setFirstLogin(false);
            }
        } else {
            if (wasFirstLogin) {
                user.setFirstLogin(false);
            } else {
                if (request.getOldPassword() == null || request.getOldPassword().isEmpty()) {
                    throw new InvalidOperationException(
                            i18nService.getMessage(MessageKey.ERROR_USER_REQUIRE_CURRENT_PASSWORD));
                }
                if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                    throw new InvalidOperationException(
                            i18nService.getMessage(MessageKey.ERROR_USER_INVALID_CURRENT_PASSWORD));
                }
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        repository.save(user);
        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());
    }

    @Cacheable(value = "users_by_role", key = "#role.name()")
    @Transactional(readOnly = true)
    public List<UserResponseDTO> findByRole(Role role) {
        if (Role.USER.equals(role)) {
            return repository.findProjectedByRoleInAndIsHiddenFalse(List.of(Role.USER, Role.ELEVATED)).stream()
                    .map(userMapper::toResponseDTO)
                    .toList();
        }
        return repository.findProjectedByRoleAndIsHiddenFalse(role).stream()
                .map(userMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDTO> findHiddenUsers(Pageable pageable) {
        Page<UserResponseDTO> page = repository.findByIsHiddenTrue(pageable)
                .map(userMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

        @Caching(evict = {
            @CacheEvict(value = "user", key = "#id"),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "users_no_teacher", allEntries = true)
        })
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void toggleUserHiddenStatus(Integer id, boolean hidden) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { id })));

        // Validación de seguridad: no se puede ocultar el último admin
        if (hidden && Role.ADMIN.equals(user.getRole())) {
            long visibleAdmins = repository.countByRoleAndIsHiddenFalse(Role.ADMIN);
            if (visibleAdmins <= 1) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_HIDE_LAST_ADMIN));
            }
        }

        user.setHidden(hidden);
        repository.save(user);
        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());
    }

    private void validateTeacherAssignment(Role userRole, Integer teacherId) {
        if (teacherId != null) {
            // Un usuario con rol CHEF o ADMIN no puede tener un profesor asignado
            if (Role.CHEF.equals(userRole) || Role.ADMIN.equals(userRole)) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_ADMIN_CANNOT_HAVE_TEACHER));
            }
            User teacher = repository.findById(teacherId)
                    .orElseThrow(
                            () -> new InvalidOperationException(i18nService
                                     .getMessage(MessageKey.ERROR_USER_TEACHER_NOT_FOUND, new Object[] { teacherId })));
            // El profesor debe tener rol CHEF
            if (!Role.CHEF.equals(teacher.getRole())) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_MUST_BE_ADMIN));
            }
        }
    }

        @Caching(evict = {
            @CacheEvict(value = "user", key = "#userId"),
            @CacheEvict(value = "users_no_teacher", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true)
        })
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void assignTeacher(Integer userId, Integer teacherId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { userId })));

        if (teacherId == null) {
            user.setTeacher(null);
        } else {
            validateTeacherAssignment(user.getRole(), teacherId);
            User teacher = repository.findById(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException(i18nService
                            .getMessage(MessageKey.ERROR_USER_TEACHER_NOT_FOUND, new Object[] { teacherId })));
            user.setTeacher(teacher);
        }

        repository.save(user);
    }

        @Caching(evict = {
            @CacheEvict(value = "user", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "users_no_teacher", allEntries = true)
        })
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public BatchTeacherAssignmentResponseDTO assignTeacherBatch(BatchTeacherAssignmentRequestDTO request) {
        List<Integer> studentIds = request.getStudentIds();
        Integer teacherId = request.getTeacherId();
        int totalCount = studentIds.size();

        // Validar el profesor primero (si se proporcionó)
        User teacher = null;
        if (teacherId != null) {
            teacher = repository.findById(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_NOT_FOUND,
                                    new Object[] { teacherId })));
            if (!Role.CHEF.equals(teacher.getRole())) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_MUST_BE_ADMIN));
            }
        }

        // Validar todos los alumnos antes de aplicar cambios
        List<User> students = repository.findAllById(studentIds);
        if (students.size() != studentIds.size()) {
            List<Integer> foundIds = students.stream().map(User::getId).toList();
            List<Integer> missingIds = studentIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new ResourceNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { missingIds }));
        }

        for (User student : students) {
            if (teacherId != null && (Role.CHEF.equals(student.getRole()) || Role.ADMIN.equals(student.getRole()))) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_ADMIN_CANNOT_HAVE_TEACHER));
            }
        }

        // Asignar el profesor a todos los alumnos validados
        final User finalTeacher = teacher;
        students.forEach(s -> s.setTeacher(finalTeacher));
        repository.saveAll(students);

        String message = finalTeacher != null
                ? "Todos los alumnos fueron asignados al profesor correctamente"
                : "Profesor desasignado correctamente de todos los alumnos";

        return BatchTeacherAssignmentResponseDTO.builder()
                .success(true)
                .processedCount(totalCount)
                .totalCount(totalCount)
                .message(message)
                .failedStudentIds(List.of())
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getMyStudents(String username) {
        User teacher = findByUsername(username);
        if (!Role.CHEF.equals(teacher.getRole())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_ONLY_ADMIN_HAS_STUDENTS));
        }
        return repository.findProjectedByTeacherIdAndIsHiddenFalse(teacher.getId()).stream()
                .map(userMapper::toResponseDTO)
                .toList();
    }

    @Cacheable(value = "user_stats", key = "'global'")
    @Transactional(readOnly = true)
    public UserStatsResponseDTO getUserStats() {
        long total = repository.count();
        var counts = repository.countUsersByRole();
        return statsMapper.toUserStatsDTO(total, counts);
    }

    @Caching(evict = {
            @CacheEvict(value = "user", key = "#userId"),
            @CacheEvict(value = "userByEmail", allEntries = true),
            @CacheEvict(value = "users_by_role", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "user_stats", allEntries = true)
    })
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void escalateRole(Integer userId, RoleEscalationRequestDTO request) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { userId })));

        if (Role.ELEVATED.equals(user.getRole())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_ALREADY_ELEVATED));
        }
        if (Role.ADMIN.equals(user.getRole())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_CANNOT_ESCALATE_ADMIN));
        }

        int maxEscalationMinutes = resolveMaxEscalationMinutes();
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > maxEscalationMinutes) {
            throw new InvalidOperationException(i18nService.getMessage(
                    MessageKey.ERROR_ESCALATION_DURATION_EXCEEDS_MAX,
                    new Object[] { maxEscalationMinutes }));
        }

        user.setRole(Role.ELEVATED);
        repository.save(user);

        TemporaryRoleEscalation escalation = escalationRepository.findByUserId(userId)
                .map(existing -> {
                    existing.setExpirationTime(escalationMapper.toEntity(request, user).getExpirationTime());
                    return existing;
                })
                .orElseGet(() -> escalationMapper.toEntity(request, user));

        escalationRepository.save(escalation);

        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());

        notifyRoleEscalationChange(user, Role.ELEVATED, "MANUAL_GRANTED");
    }

        @Caching(evict = {
            @CacheEvict(value = "user", key = "#userId"),
            @CacheEvict(value = "userByEmail", allEntries = true),
            @CacheEvict(value = "users_by_role", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "user_stats", allEntries = true)
        })
    @Transactional(rollbackFor = { ResourceNotFoundException.class })
    public void deescalateRole(Integer userId) {
        deescalateRole(userId, "MANUAL_REVOKED");
        }

        @Caching(evict = {
            @CacheEvict(value = "user", key = "#userId"),
            @CacheEvict(value = "userByEmail", allEntries = true),
            @CacheEvict(value = "users_by_role", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "user_stats", allEntries = true)
        })
        @Transactional(rollbackFor = { ResourceNotFoundException.class })
        public void deescalateRole(Integer userId, String reason) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { userId })));

        if (!Role.ELEVATED.equals(user.getRole())) {

            return;
        }

        user.setRole(Role.USER);
        repository.save(user);

        escalationRepository.deleteByUserId(userId);

        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());

        notifyRoleEscalationChange(user, Role.USER, reason);
    }

    private void notifyRoleEscalationChange(User user, Role newRole, String reason) {
        RoleNotificationMessage message = RoleNotificationMessage.builder()
                .title("Cambio de permisos")
                .message("Tus permisos han sido actualizados a " + newRole.name() + ".")
                .code(AlertCode.ROLE_ESCALATION_CHANGED)
                .newRole(newRole.name())
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();

        roleNotificationService.sendNotificationToUser(user.getName(), message);
    }

    @Cacheable(value = "users_no_teacher", key = "'all'")
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getStudentsWithoutTeacher() {
        List<UserProjection> students = repository.findProjectedByTeacherIsNullAndRoleInAndIsHiddenFalse(
                List.of(Role.USER, Role.ELEVATED));
        return students.stream()
                .map(userMapper::toResponseDTO)
                .toList();
    }

        @Caching(evict = {
            @CacheEvict(value = "user", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "userByEmail", allEntries = true),
            @CacheEvict(value = "users_no_teacher", allEntries = true)
        })
    public BatchTeacherAssignmentResponseDTO hideAllStudentsOfTeacher(Integer teacherId, boolean hidden) {
        User teacher = repository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, teacherId)));

        if (teacher.getRole() != Role.CHEF) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_MUST_BE_ADMIN));
        }

        List<User> students = repository.findByTeacherId(teacherId);
        List<User> toUpdate = students.stream()
                .filter(s -> s.isHidden() != hidden)
                .peek(s -> s.setHidden(hidden))
                .toList();

        if (!toUpdate.isEmpty()) {
            repository.saveAll(toUpdate);
        }

        return BatchTeacherAssignmentResponseDTO.builder()
                .success(true)
                .processedCount(toUpdate.size())
                .totalCount(students.size())
                .message(i18nService.getMessage(MessageKey.ERROR_USER_STUDENTS_HIDDEN_SUCCESS, toUpdate.size()))
                .failedStudentIds(List.of())
                .build();
    }

    @Transactional(rollbackFor = { RuntimeException.class, Exception.class })
        @Caching(evict = {
            @CacheEvict(value = "user", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "userByEmail", allEntries = true),
            @CacheEvict(value = "users_no_teacher", allEntries = true)
        })
    public BatchTeacherAssignmentResponseDTO transferStudents(TransferStudentsRequestDTO request) {
        if (request.getFromTeacherId().equals(request.getToTeacherId())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_TRANSFER_SAME));
        }

        User fromTeacher = repository.findById(request.getFromTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, request.getFromTeacherId())));

        User toTeacher = repository.findById(request.getToTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, request.getToTeacherId())));

        if (fromTeacher.getRole() != Role.CHEF || toTeacher.getRole() != Role.CHEF) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_MUST_BE_ADMIN));
        }

        List<User> students = repository.findAllById(request.getStudentIds());
        List<Integer> failedIds = new ArrayList<>();

        for (User student : students) {
            if (student.getTeacher() == null || !student.getTeacher().getId().equals(request.getFromTeacherId())) {
                throw new InvalidOperationException(i18nService.getMessage(
                        MessageKey.ERROR_USER_STUDENT_NOT_BELONGS_TO_TEACHER, student.getId(), fromTeacher.getId()));
            }
            student.setTeacher(toTeacher);
        }

        repository.saveAll(students);

        return BatchTeacherAssignmentResponseDTO.builder()
                .success(true)
                .processedCount(students.size())
                .totalCount(request.getStudentIds().size())
                .message("Transferencia completada con éxito")
                .failedStudentIds(failedIds)
                .build();
    }

        @Caching(evict = {
            @CacheEvict(value = "user", allEntries = true),
            @CacheEvict(value = "users_page", allEntries = true),
            @CacheEvict(value = "userByEmail", allEntries = true),
            @CacheEvict(value = "users_no_teacher", allEntries = true)
        })
    public BatchTeacherAssignmentResponseDTO transferAllStudents(Integer fromTeacherId, Integer toTeacherId) {
        if (fromTeacherId.equals(toTeacherId)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_TRANSFER_SAME));
        }

        User fromTeacher = repository.findById(fromTeacherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, fromTeacherId)));

        User toTeacher = repository.findById(toTeacherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, toTeacherId)));

        if (fromTeacher.getRole() != Role.CHEF || toTeacher.getRole() != Role.CHEF) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_MUST_BE_ADMIN));
        }

        List<User> students = repository.findByTeacherId(fromTeacherId);
        students.forEach(s -> s.setTeacher(toTeacher));

        if (!students.isEmpty()) {
            repository.saveAll(students);
        }

        return BatchTeacherAssignmentResponseDTO.builder()
                .success(true)
                .processedCount(students.size())
                .totalCount(students.size())
                .message("Transferencia total completada con éxito")
                .failedStudentIds(List.of())
                .build();
    }

    private void validatePasswordLength(String password) {
        int minLength = resolveMinPasswordLength();
        if (password == null || password.length() < minLength) {
            throw new InvalidOperationException(i18nService.getMessage(
                    MessageKey.ERROR_PASSWORD_TOO_SHORT,
                    new Object[] { minLength }));
        }
    }

    private int resolveMinPasswordLength() {
        if (systemConfigService == null) {
            return 6;
        }
        try {
            return systemConfigService.getMinPasswordLength();
        } catch (Exception ignored) {
            return 6;
        }
    }

    private int resolveMaxEscalationMinutes() {
        if (systemConfigService == null) {
            return 1440;
        }
        try {
            return systemConfigService.getMaxEscalationMinutes();
        } catch (Exception ignored) {
            return 1440;
        }
    }
}