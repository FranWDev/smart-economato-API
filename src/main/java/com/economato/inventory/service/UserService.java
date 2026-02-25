package com.economato.inventory.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.request.UserRequestDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.dto.response.UserStatsResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.StatsMapper;
import com.economato.inventory.mapper.TemporaryRoleEscalationMapper;
import com.economato.inventory.mapper.UserMapper;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import com.economato.inventory.dto.request.RoleEscalationRequestDTO;
import com.economato.inventory.model.TemporaryRoleEscalation;
import com.economato.inventory.repository.TemporaryRoleEscalationRepository;

@Service
@Transactional(rollbackFor = { RuntimeException.class, Exception.class })
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final StatsMapper statsMapper;
    private final TemporaryRoleEscalationMapper escalationMapper;
    private final CustomUserDetailsService customUserDetailsService;
    private final TemporaryRoleEscalationRepository escalationRepository;
    private final TaskScheduler taskScheduler;

    // Map to keep track of scheduled futures so we can cancel them if deescalated
    // early
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, UserMapper userMapper,
            TemporaryRoleEscalationMapper escalationMapper,
            StatsMapper statsMapper,
            CustomUserDetailsService customUserDetailsService,
            TemporaryRoleEscalationRepository escalationRepository,
            TaskScheduler taskScheduler) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.escalationMapper = escalationMapper;
        this.statsMapper = statsMapper;
        this.customUserDetailsService = customUserDetailsService;
        this.escalationRepository = escalationRepository;
        this.taskScheduler = taskScheduler;
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll(Pageable pageable) {
        return repository.findByIsHiddenFalse(pageable).stream()
                .map(userMapper::toResponseDTO)
                .toList();
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
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findCurrentUser(String username) {
        return userMapper.toResponseDTO(findByUsername(username));
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public UserResponseDTO save(UserRequestDTO requestDTO) {

        if (repository.existsByUser(requestDTO.getUser())) {
            throw new InvalidOperationException("Ya existe un usuario con el usuario: " + requestDTO.getUser());
        }

        if (repository.findByName(requestDTO.getName()).isPresent()) {
            throw new InvalidOperationException("Ya existe un usuario con el nombre: " + requestDTO.getName());
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
                                "Ya existe un usuario con el usuario: " + requestDTO.getUser());
                    }

                    if (!existing.getName().equals(requestDTO.getName()) &&
                            repository.findByName(requestDTO.getName()).isPresent()) {
                        throw new InvalidOperationException(
                                "Ya existe un usuario con el nombre: " + requestDTO.getName());
                    }

                    userMapper.updateEntity(requestDTO, existing);

                    if (requestDTO.getPassword() != null && !requestDTO.getPassword().isEmpty()) {
                        existing.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
                    }

                    validateTeacherAssignment(existing.getRole(), requestDTO.getTeacherId());

                    return userMapper.toResponseDTO(repository.save(existing));
                });
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {

        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (Role.ADMIN.equals(user.getRole())) {
            long adminCount = repository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new InvalidOperationException("No se puede eliminar el último administrador del sistema");
            }
        }

        repository.deleteById(id);
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void updateFirstLoginStatus(Integer id, boolean status, boolean isAdmin) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // Validación de seguridad: solo un admin puede cambiar firstLogin de false a
        // true
        // Un usuario normal solo puede marcarlo como false (completar primer login)
        if (!isAdmin && status && !user.isFirstLogin()) {
            throw new InvalidOperationException(
                    "No se permite reactivar el estado de primer login. Solo los administradores pueden realizar esta acción.");
        }

        user.setFirstLogin(status);
        repository.save(user);
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void changePassword(Integer id, com.economato.inventory.dto.request.ChangePasswordRequestDTO request,
            boolean isAdmin) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (isAdmin) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        } else {
            if (user.isFirstLogin()) {
                user.setFirstLogin(false);
            } else {
                if (request.getOldPassword() == null || request.getOldPassword().isEmpty()) {
                    throw new InvalidOperationException("Se requiere la contraseña actual");
                }
                if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                    throw new InvalidOperationException("La contraseña actual es incorrecta");
                }
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        repository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findByRole(Role role) {
        return repository.findProjectedByRoleAndIsHiddenFalse(role).stream()
                .map(userMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findHiddenUsers(Pageable pageable) {
        return repository.findByIsHiddenTrue(pageable).stream()
                .map(userMapper::toResponseDTO)
                .toList();
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void toggleUserHiddenStatus(Integer id, boolean hidden) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // Validación de seguridad: no se puede ocultar el último admin
        if (hidden && Role.ADMIN.equals(user.getRole())) {
            long visibleAdmins = repository.findByIsHiddenFalse(Pageable.unpaged()).stream()
                    .filter(p -> p.getRole() == Role.ADMIN)
                    .count();
            if (visibleAdmins <= 1) {
                throw new InvalidOperationException("No se puede ocultar el último administrador visible del sistema");
            }
        }

        user.setHidden(hidden);
        repository.save(user);
    }

    private void validateTeacherAssignment(Role userRole, Integer teacherId) {
        if (teacherId != null) {
            if (Role.ADMIN.equals(userRole)) {
                throw new InvalidOperationException("Un usuario con rol ADMIN no puede tener un profesor asignado.");
            }
            User teacher = repository.findById(teacherId)
                    .orElseThrow(
                            () -> new InvalidOperationException("El profesor asignado no existe con ID: " + teacherId));
            if (!Role.ADMIN.equals(teacher.getRole())) {
                throw new InvalidOperationException("El usuario asignado como profesor no tiene el rol ADMIN.");
            }
        }
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void assignTeacher(Integer userId, Integer teacherId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));

        if (teacherId == null) {
            user.setTeacher(null);
        } else {
            validateTeacherAssignment(user.getRole(), teacherId);
            User teacher = repository.findById(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado con ID: " + teacherId));
            user.setTeacher(teacher);
        }

        repository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getMyStudents(String username) {
        User teacher = findByUsername(username);
        if (!Role.ADMIN.equals(teacher.getRole())) {
            throw new InvalidOperationException("Solo los usuarios con rol ADMIN tienen alumnos.");
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
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));

        if (Role.CHEF.equals(user.getRole())) {
            throw new InvalidOperationException("El usuario ya tiene rol CHEF.");
        }
        if (Role.ADMIN.equals(user.getRole())) {
            throw new InvalidOperationException("No se puede escalar a un administrador.");
        }

        user.setRole(Role.CHEF);
        repository.save(user);

        TemporaryRoleEscalation escalation = escalationRepository.findByUserId(userId)
                .map(existing -> {
                    existing.setExpirationTime(escalationMapper.toEntity(request, user).getExpirationTime());
                    return existing;
                })
                .orElseGet(() -> escalationMapper.toEntity(request, user));

        escalationRepository.save(escalation);

        LocalDateTime expirationTime = escalation.getExpirationTime();

        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());

        scheduleDeescalation(userId, expirationTime);
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class })
    public void deescalateRole(Integer userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));

        if (!Role.CHEF.equals(user.getRole())) {
            // Already de-escalated or not a CHEF
            return;
        }

        user.setRole(Role.USER);
        repository.save(user);

        escalationRepository.deleteByUserId(userId);

        customUserDetailsService.evictUser(user.getName());
        customUserDetailsService.evictUser(user.getUser());

        cancelScheduledTask(userId);
    }

    private void scheduleDeescalation(Integer userId, LocalDateTime expirationTime) {
        cancelScheduledTask(userId);

        java.time.Instant executionTime = expirationTime.atZone(ZoneId.systemDefault()).toInstant();

        ScheduledFuture<?> future = taskScheduler.schedule(() -> deescalateRole(userId), executionTime);
        scheduledTasks.put(userId, future);
    }

    private void cancelScheduledTask(Integer userId) {
        ScheduledFuture<?> future = scheduledTasks.remove(userId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reschedulePendingEscalationsOnStartup() {
        System.out.println("====== STARTING ROLE ESCALATION SCHEDULE SYNC ======");
        List<TemporaryRoleEscalation> activeEscalations = escalationRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (TemporaryRoleEscalation escalation : activeEscalations) {
            Integer userId = escalation.getUser().getId();

            if (escalation.getExpirationTime().isBefore(now)) {
                System.out.println("Deescalating expired user ID " + userId);
                deescalateRole(userId);
            } else {
                System.out.println("Rescheduling active escalation for user ID " + userId);
                scheduleDeescalation(userId, escalation.getExpirationTime());
            }
        }
    }
}