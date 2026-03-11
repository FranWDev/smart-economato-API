package com.economato.inventory.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    public UserService(I18nService i18nService, UserRepository repository, PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            TemporaryRoleEscalationMapper escalationMapper,
            StatsMapper statsMapper,
            CustomUserDetailsService customUserDetailsService,
            TemporaryRoleEscalationRepository escalationRepository) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.escalationMapper = escalationMapper;
        this.statsMapper = statsMapper;
        this.customUserDetailsService = customUserDetailsService;
        this.escalationRepository = escalationRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDTO> findAll(Pageable pageable) {
        Page<UserResponseDTO> page = repository.findByIsHiddenFalse(pageable)
                .map(userMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @Cacheable(value = "user", key = "#id")
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

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public UserResponseDTO save(UserRequestDTO requestDTO) {

        if (repository.existsByUser(requestDTO.getUser())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_USER_ALREADY_EXISTS));
        }

        if (repository.findByName(requestDTO.getName()).isPresent()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_USER_ALREADY_EXISTS));
        }

        User user = userMapper.toEntity(requestDTO);
        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        user.setFirstLogin(true); // Default to true on creation

        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }

        validateTeacherAssignment(user.getRole(), requestDTO.getTeacherId());

        return userMapper.toResponseDTO(repository.save(user));
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
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
                        existing.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
                    }

                    validateTeacherAssignment(existing.getRole(), requestDTO.getTeacherId());

                    User updatedUser = repository.save(existing);
                    customUserDetailsService.evictUser(updatedUser.getName());
                    customUserDetailsService.evictUser(updatedUser.getUser());
                    return userMapper.toResponseDTO(updatedUser);
                });
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
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

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void updateFirstLoginStatus(Integer id, boolean status, boolean isAdmin) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { id })));

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

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void changePassword(Integer id, ChangePasswordRequestDTO request,
            boolean isAdmin) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { id })));

        if (isAdmin) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        } else {
            if (user.isFirstLogin()) {
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

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void toggleUserHiddenStatus(Integer id, boolean hidden) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { id })));

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
                            () -> new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_NOT_FOUND, new Object[] { teacherId })));
            // El profesor debe tener rol CHEF
            if (!Role.CHEF.equals(teacher.getRole())) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_MUST_BE_ADMIN));
            }
        }
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void assignTeacher(Integer userId, Integer teacherId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { userId })));

        if (teacherId == null) {
            user.setTeacher(null);
        } else {
            validateTeacherAssignment(user.getRole(), teacherId);
            User teacher = repository.findById(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_NOT_FOUND, new Object[] { teacherId })));
            user.setTeacher(teacher);
        }

        repository.save(user);
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
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
                            i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_NOT_FOUND, new Object[] { teacherId })));
            if (!Role.CHEF.equals(teacher.getRole())) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_MUST_BE_ADMIN));
            }
        }

        // Validar todos los alumnos antes de aplicar cambios
        List<User> students = new ArrayList<>();
        for (Integer studentId : studentIds) {
            User student = repository.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { studentId })));
            if (teacherId != null && (Role.CHEF.equals(student.getRole()) || Role.ADMIN.equals(student.getRole()))) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_ADMIN_CANNOT_HAVE_TEACHER));
            }
            students.add(student);
        }

        // Asignar el profesor a todos los alumnos validados
        final User finalTeacher = teacher;
        students.forEach(s -> {
            s.setTeacher(finalTeacher);
            repository.save(s);
        });

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

    @Transactional(readOnly = true)
    public UserStatsResponseDTO getUserStats() {
        long total = repository.count();
        var counts = repository.countUsersByRole();
        return statsMapper.toUserStatsDTO(total, counts);
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void escalateRole(Integer userId, RoleEscalationRequestDTO request) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { userId })));

        if (Role.ELEVATED.equals(user.getRole())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_ALREADY_ELEVATED));
        }
        if (Role.ADMIN.equals(user.getRole())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_CANNOT_ESCALATE_ADMIN));
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
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class })
    public void deescalateRole(Integer userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND, new Object[] { userId })));

        if (!Role.ELEVATED.equals(user.getRole())) {
            // Already de-escalated or not a ELEVATED
            return;
        }

        user.setRole(Role.USER);
        repository.save(user);

        escalationRepository.deleteByUserId(userId);

        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());
    }

}