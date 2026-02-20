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
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.UserMapper;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = { RuntimeException.class, Exception.class })
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "user", key = "#id")
    @Transactional(readOnly = true)
    public Optional<UserResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(this::toResponseDTO);
    }

    @Cacheable(value = "userByEmail", key = "#username")
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return repository.findByName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
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
    @Transactional(rollbackFor = { ResourceNotFoundException.class })
    public void updateFirstLoginStatus(Integer id, boolean status) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        user.setFirstLogin(status);
        repository.save(user);
    }

    @CacheEvict(value = { "users", "user", "userByEmail" }, allEntries = true)
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void changePassword(Integer id, com.economato.inventory.dto.request.ChangePasswordRequestDTO request,
            boolean isAdmin) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // Si es admin, puede cambiar la contraseña sin la antigua
        if (isAdmin) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            // Si el admin cambia la contraseña, podríamos querer resetear firstLogin a true
            // o false dependiendo de la logica de negocio
            // En este caso, asumiremos que si el admin la resetea, el usuario tendra que
            // cambiarla de nuevo?
            // "y el usuario si es para cambiar su propia contraseña... isFirstLogin es
            // false"
            // "en caso de ser el rol admin, no debe requerir enviarse la antigua
            // contraseña"
            // No se especifica si cambia isFirstLogin para admin. Asumimos que no cambia o
            // se mantiene.
            // Pero si el usuario la cambia (siendo firstLogin=true), pasa a false.
        } else {
            // Es el propio usuario
            if (user.isFirstLogin()) {
                // Si es primer login, no pide contraseña antigua
                user.setFirstLogin(false);
            } else {
                // Si no es primer login, DEBE pedir contraseña antigua
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
        return repository.findProjectedByRole(role).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convierte una proyección de User a UserResponseDTO.
     */
    private UserResponseDTO toResponseDTO(com.economato.inventory.dto.projection.UserProjection projection) {
        com.economato.inventory.dto.response.UserResponseDTO dto = new com.economato.inventory.dto.response.UserResponseDTO();
        dto.setId(projection.getId());
        dto.setName(projection.getName());
        dto.setUser(projection.getUser());
        dto.setFirstLogin(projection.getIsFirstLogin());
        dto.setRole(projection.getRole());
        return dto;
    }
}