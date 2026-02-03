package com.economatom.inventory.service;

import com.economatom.inventory.dto.request.UserRequestDTO;
import com.economatom.inventory.dto.response.UserResponseDTO;
import com.economatom.inventory.exception.InvalidOperationException;
import com.economatom.inventory.mapper.UserMapper;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserRequestDTO testUserRequestDTO;
    private UserResponseDTO testUserResponseDTO;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setName("Test User");
        testUser.setEmail("test@test.com");
        testUser.setPassword("encodedPassword");
        testUser.setRole("USER");

        testUserRequestDTO = new UserRequestDTO();
        testUserRequestDTO.setName("Test User");
        testUserRequestDTO.setEmail("test@test.com");
        testUserRequestDTO.setPassword("password123");
        testUserRequestDTO.setRole("USER");

        testUserResponseDTO = new UserResponseDTO();
        testUserResponseDTO.setId(1);
        testUserResponseDTO.setName("Test User");
        testUserResponseDTO.setEmail("test@test.com");
        testUserResponseDTO.setRole("USER");
    }

    @Test
    void findAll_ShouldReturnListOfUsers() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(Arrays.asList(testUser));
        when(repository.findAll(pageable)).thenReturn(page);
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);

        List<UserResponseDTO> result = userService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUserResponseDTO.getName(), result.get(0).getName());
        verify(repository).findAll(pageable);
        verify(userMapper).toResponseDTO(testUser);
    }

    @Test
    void findById_WhenUserExists_ShouldReturnUser() {

        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);

        Optional<UserResponseDTO> result = userService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testUserResponseDTO.getName(), result.get().getName());
        verify(repository).findById(1);
    }

    @Test
    void findById_WhenUserDoesNotExist_ShouldReturnEmpty() {

        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<UserResponseDTO> result = userService.findById(999);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
    }

    @Test
    void findByUsername_WhenUserExists_ShouldReturnUser() {

        when(repository.findByName("Test User")).thenReturn(Optional.of(testUser));

        User result = userService.findByUsername("Test User");

        assertNotNull(result);
        assertEquals(testUser.getName(), result.getName());
        verify(repository).findByName("Test User");
    }

    @Test
    void findByUsername_WhenUserDoesNotExist_ShouldThrowException() {

        when(repository.findByName("NonExistent")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            userService.findByUsername("NonExistent");
        });
        verify(repository).findByName("NonExistent");
    }

    @Test
    void save_WhenEmailDoesNotExist_ShouldCreateUser() {

        when(repository.existsByEmail(testUserRequestDTO.getEmail())).thenReturn(false);
        when(userMapper.toEntity(testUserRequestDTO)).thenReturn(testUser);
        when(passwordEncoder.encode(testUserRequestDTO.getPassword())).thenReturn("encodedPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);

        UserResponseDTO result = userService.save(testUserRequestDTO);

        assertNotNull(result);
        assertEquals(testUserResponseDTO.getName(), result.getName());
        verify(repository).existsByEmail(testUserRequestDTO.getEmail());
        verify(passwordEncoder).encode(testUserRequestDTO.getPassword());
        verify(repository).save(any(User.class));
    }

    @Test
    void save_WhenEmailExists_ShouldThrowException() {

        when(repository.existsByEmail(testUserRequestDTO.getEmail())).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> {
            userService.save(testUserRequestDTO);
        });
        verify(repository).existsByEmail(testUserRequestDTO.getEmail());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void save_WhenRoleIsNull_ShouldSetDefaultRole() {

        testUserRequestDTO.setRole(null);
        User userWithNullRole = new User();
        userWithNullRole.setName("Test User");
        userWithNullRole.setEmail("test@test.com");
        userWithNullRole.setRole(null);

        when(repository.existsByEmail(testUserRequestDTO.getEmail())).thenReturn(false);
        when(userMapper.toEntity(testUserRequestDTO)).thenReturn(userWithNullRole);
        when(passwordEncoder.encode(testUserRequestDTO.getPassword())).thenReturn("encodedPassword");
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(1);
            return savedUser;
        });
        when(userMapper.toResponseDTO(any(User.class))).thenReturn(testUserResponseDTO);

        UserResponseDTO result = userService.save(testUserRequestDTO);

        assertNotNull(result);
        verify(repository).save(argThat(user -> "USER".equals(user.getRole())));
    }

    @Test
    void save_WhenRoleIsEmpty_ShouldSetDefaultRole() {

        testUserRequestDTO.setRole("");
        User userWithEmptyRole = new User();
        userWithEmptyRole.setName("Test User");
        userWithEmptyRole.setEmail("test@test.com");
        userWithEmptyRole.setRole("");

        when(repository.existsByEmail(testUserRequestDTO.getEmail())).thenReturn(false);
        when(userMapper.toEntity(testUserRequestDTO)).thenReturn(userWithEmptyRole);
        when(passwordEncoder.encode(testUserRequestDTO.getPassword())).thenReturn("encodedPassword");
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(1);
            return savedUser;
        });
        when(userMapper.toResponseDTO(any(User.class))).thenReturn(testUserResponseDTO);

        UserResponseDTO result = userService.save(testUserRequestDTO);

        assertNotNull(result);
        verify(repository).save(argThat(user -> "USER".equals(user.getRole())));
    }

    @Test
    void update_WhenUserExists_ShouldUpdateUser() {

        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);
        doNothing().when(userMapper).updateEntity(testUserRequestDTO, testUser);
        when(passwordEncoder.encode(testUserRequestDTO.getPassword())).thenReturn("newEncodedPassword");

        Optional<UserResponseDTO> result = userService.update(1, testUserRequestDTO);

        assertTrue(result.isPresent());
        assertEquals(testUserResponseDTO.getName(), result.get().getName());
        verify(repository).findById(1);
        verify(userMapper).updateEntity(testUserRequestDTO, testUser);
        verify(passwordEncoder).encode(testUserRequestDTO.getPassword());
    }

    @Test
    void update_WhenPasswordIsNull_ShouldNotEncodePassword() {

        testUserRequestDTO.setPassword(null);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);
        doNothing().when(userMapper).updateEntity(testUserRequestDTO, testUser);

        Optional<UserResponseDTO> result = userService.update(1, testUserRequestDTO);

        assertTrue(result.isPresent());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void update_WhenPasswordIsEmpty_ShouldNotEncodePassword() {

        testUserRequestDTO.setPassword("");
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);
        doNothing().when(userMapper).updateEntity(testUserRequestDTO, testUser);

        Optional<UserResponseDTO> result = userService.update(1, testUserRequestDTO);

        assertTrue(result.isPresent());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void update_WhenUserDoesNotExist_ShouldReturnEmpty() {

        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<UserResponseDTO> result = userService.update(999, testUserRequestDTO);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void deleteById_ShouldCallRepository() {

        doNothing().when(repository).deleteById(1);

        userService.deleteById(1);

        verify(repository).deleteById(1);
    }

    @Test
    void findByRole_ShouldReturnUsersWithRole() {

        List<User> users = Arrays.asList(testUser);
        when(repository.findByRole("USER")).thenReturn(users);
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);

        List<UserResponseDTO> result = userService.findByRole("USER");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUserResponseDTO.getRole(), result.get(0).getRole());
        verify(repository).findByRole("USER");
    }

    @Test
    void findByRole_WhenNoUsersFound_ShouldReturnEmptyList() {

        when(repository.findByRole("NONEXISTENT")).thenReturn(Arrays.asList());

        List<UserResponseDTO> result = userService.findByRole("NONEXISTENT");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findByRole("NONEXISTENT");
    }
}
