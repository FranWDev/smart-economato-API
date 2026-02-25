package com.economato.inventory.service;

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

import com.economato.inventory.dto.projection.UserProjection;
import com.economato.inventory.dto.request.UserRequestDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.UserMapper;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.repository.TemporaryRoleEscalationRepository;
import com.economato.inventory.model.TemporaryRoleEscalation;
import com.economato.inventory.mapper.TemporaryRoleEscalationMapper;
import com.economato.inventory.dto.request.RoleEscalationRequestDTO;
import org.springframework.scheduling.TaskScheduler;
import java.util.concurrent.ScheduledFuture;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private TemporaryRoleEscalationRepository escalationRepository;

    @Mock
    private TemporaryRoleEscalationMapper escalationMapper;

    @Mock
    private TaskScheduler taskScheduler;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserRequestDTO testUserRequestDTO;
    private UserResponseDTO testUserResponseDTO;
    private UserProjection testProjection;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setName("Test User");
        testUser.setUser("testUser");
        testUser.setPassword("encodedPassword");
        testUser.setRole(Role.USER);

        testUserRequestDTO = new UserRequestDTO();
        testUserRequestDTO.setName("Test User");
        testUserRequestDTO.setUser("testUser");
        testUserRequestDTO.setPassword("password123");
        testUserRequestDTO.setRole(Role.USER);

        testUserResponseDTO = new UserResponseDTO();
        testUserResponseDTO.setId(1);
        testUserResponseDTO.setName("Test User");
        testUserResponseDTO.setUser("testUser");
        testUserResponseDTO.setRole(Role.USER);

        testProjection = mock(UserProjection.class);
        lenient().when(testProjection.getId()).thenReturn(1);
        lenient().when(testProjection.getName()).thenReturn("Test User");
        lenient().when(testProjection.getUser()).thenReturn("testUser");
        lenient().when(testProjection.getRole()).thenReturn(Role.USER);
    }

    @Test
    void findAll_ShouldReturnListOfUsers() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<UserProjection> page = new PageImpl<>(Arrays.asList(testProjection));
        when(repository.findByIsHiddenFalse(pageable)).thenReturn(page);
        when(userMapper.toResponseDTO(any(UserProjection.class))).thenReturn(testUserResponseDTO);

        List<UserResponseDTO> result = userService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUserResponseDTO.getUser(), result.get(0).getUser());
        verify(repository).findByIsHiddenFalse(pageable);
    }

    @Test
    void findById_WhenUserExists_ShouldReturnUser() {

        when(repository.findProjectedById(1)).thenReturn(Optional.of(testProjection));
        when(userMapper.toResponseDTO(any(UserProjection.class))).thenReturn(testUserResponseDTO);

        Optional<UserResponseDTO> result = userService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testUserResponseDTO.getName(), result.get().getName());
        verify(repository).findProjectedById(1);
    }

    @Test
    void findById_WhenUserDoesNotExist_ShouldReturnEmpty() {

        when(repository.findProjectedById(999)).thenReturn(Optional.empty());

        Optional<UserResponseDTO> result = userService.findById(999);

        assertFalse(result.isPresent());
        verify(repository).findProjectedById(999);
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
    void findCurrentUser_WhenUserExists_ShouldReturnResponseDTO() {
        when(repository.findByName("Test User")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);

        UserResponseDTO result = userService.findCurrentUser("Test User");

        assertNotNull(result);
        assertEquals(testUserResponseDTO.getName(), result.getName());
        assertEquals(testUserResponseDTO.getUser(), result.getUser());
        assertEquals(testUserResponseDTO.getRole(), result.getRole());
        verify(repository).findByName("Test User");
        verify(userMapper).toResponseDTO(testUser);
    }

    @Test
    void findCurrentUser_WhenUserDoesNotExist_ShouldThrowException() {
        when(repository.findByName("NonExistent")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.findCurrentUser("NonExistent"));
    }

    @Test
    void save_WhenEmailDoesNotExist_ShouldCreateUser() {

        when(repository.existsByUser(testUserRequestDTO.getUser())).thenReturn(false);
        when(userMapper.toEntity(testUserRequestDTO)).thenReturn(testUser);
        when(passwordEncoder.encode(testUserRequestDTO.getPassword())).thenReturn("encodedPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponseDTO(testUser)).thenReturn(testUserResponseDTO);

        UserResponseDTO result = userService.save(testUserRequestDTO);

        assertNotNull(result);
        assertEquals(testUserResponseDTO.getUser(), result.getUser());
        verify(repository).existsByUser(testUserRequestDTO.getUser());
        verify(passwordEncoder).encode(testUserRequestDTO.getPassword());
        verify(repository).save(any(User.class));
    }

    @Test
    void save_WhenEmailExists_ShouldThrowException() {

        when(repository.existsByUser(testUserRequestDTO.getUser())).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> {
            userService.save(testUserRequestDTO);
        });
        verify(repository).existsByUser(testUserRequestDTO.getUser());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void save_WhenRoleIsNull_ShouldSetDefaultRole() {

        testUserRequestDTO.setRole(null);
        User userWithNullRole = new User();
        userWithNullRole.setName("Test User");
        userWithNullRole.setUser("testUser");
        userWithNullRole.setRole(null);

        when(repository.existsByUser(testUserRequestDTO.getUser())).thenReturn(false);
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
        verify(repository).save(argThat(user -> Role.USER.equals(user.getRole())));
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
        // Mock que el usuario existe y no es el último admin
        testUser.setRole(Role.USER);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        doNothing().when(repository).deleteById(1);

        userService.deleteById(1);

        verify(repository).findById(1);
        verify(repository).deleteById(1);
    }

    @Test
    void findByRole_ShouldReturnUsersWithRole() {

        List<UserProjection> users = Arrays.asList(testProjection);
        when(repository.findProjectedByRoleAndIsHiddenFalse(Role.USER)).thenReturn(users);
        when(userMapper.toResponseDTO(any(UserProjection.class))).thenReturn(testUserResponseDTO);

        List<UserResponseDTO> result = userService.findByRole(Role.USER);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUserResponseDTO.getRole(), result.get(0).getRole());
        verify(repository).findProjectedByRoleAndIsHiddenFalse(Role.USER);
    }

    @Test
    void findByRole_WhenNoUsersFound_ShouldReturnEmptyList() {

        when(repository.findProjectedByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(Arrays.asList());

        List<UserResponseDTO> result = userService.findByRole(Role.ADMIN);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findProjectedByRoleAndIsHiddenFalse(Role.ADMIN);
    }

    @Test
    void save_WhenDuplicateName_ShouldThrowException() {
        when(repository.existsByUser(testUserRequestDTO.getUser())).thenReturn(false);
        when(repository.findByName(testUserRequestDTO.getName())).thenReturn(Optional.of(testUser));

        assertThrows(InvalidOperationException.class, () -> userService.save(testUserRequestDTO));
    }

    @Test
    void update_WhenDuplicateEmail_ShouldThrowException() {
        User existingUser = new User();
        existingUser.setId(1);
        existingUser.setUser("oldUser");
        existingUser.setName("Old Name");

        UserRequestDTO updateRequest = new UserRequestDTO();
        updateRequest.setUser("anotherUser");
        updateRequest.setName("New Name");
        updateRequest.setPassword("password");
        updateRequest.setRole(Role.USER);

        when(repository.findById(1)).thenReturn(Optional.of(existingUser));
        when(repository.existsByUser("anotherUser")).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> userService.update(1, updateRequest));
    }

    @Test
    void update_WhenDuplicateName_ShouldThrowException() {
        User existingUser = new User();
        existingUser.setId(1);
        existingUser.setUser("existingUser");
        existingUser.setName("Old Name");

        UserRequestDTO updateRequest = new UserRequestDTO();
        updateRequest.setUser("newUser");
        updateRequest.setName("Another Name");
        updateRequest.setPassword("password");
        updateRequest.setRole(Role.USER);

        when(repository.findById(1)).thenReturn(Optional.of(existingUser));
        when(repository.findByName("Another Name")).thenReturn(Optional.of(new User()));

        assertThrows(InvalidOperationException.class, () -> userService.update(1, updateRequest));
    }

    @Test
    void deleteById_WhenLastAdmin_ShouldThrowException() {
        testUser.setRole(Role.ADMIN);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThrows(InvalidOperationException.class, () -> userService.deleteById(1));
        verify(repository, never()).deleteById(1);
    }

    @Test
    void deleteById_WhenNotLastAdmin_ShouldSucceed() {
        testUser.setRole(Role.ADMIN);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.countByRole(Role.ADMIN)).thenReturn(2L);
        doNothing().when(repository).deleteById(1);

        userService.deleteById(1);

        verify(repository).deleteById(1);
    }

    @Test
    void deleteById_WhenUserNotFound_ShouldThrowException() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteById(999));
        verify(repository, never()).deleteById(999);
    }

    @Test
    void changePassword_WhenAdmin_ShouldUpdatePassword() {
        String newPassword = "newPassword123";
        com.economato.inventory.dto.request.ChangePasswordRequestDTO request = new com.economato.inventory.dto.request.ChangePasswordRequestDTO();
        request.setNewPassword(newPassword);

        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(1, request, true);

        verify(passwordEncoder).encode(newPassword);
        verify(repository).save(testUser);
        // Admin change doesn't update firstLogin status in current logic, verify it
        // stays as is
        // or whatever default behavior. Here testUser default is not specified but
        // let's assume it doesn't throw.
    }

    @Test
    void changePassword_WhenUserFirstLogin_ShouldUpdatePasswordAndSetFirstLoginFalse() {
        String newPassword = "newPassword123";
        com.economato.inventory.dto.request.ChangePasswordRequestDTO request = new com.economato.inventory.dto.request.ChangePasswordRequestDTO();
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(true);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(1, request, false);

        verify(passwordEncoder).encode(newPassword);
        verify(repository).save(testUser);
        assertFalse(testUser.isFirstLogin());
    }

    @Test
    void changePassword_WhenUserNotFirstLoginAndCorrectOldPassword_ShouldUpdatePassword() {
        String oldPassword = "oldPassword";
        String newPassword = "newPassword123";
        com.economato.inventory.dto.request.ChangePasswordRequestDTO request = new com.economato.inventory.dto.request.ChangePasswordRequestDTO();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(false);
        testUser.setPassword("encodedOldPassword");
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(oldPassword, "encodedOldPassword")).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(1, request, false);

        verify(passwordEncoder).matches(oldPassword, "encodedOldPassword");
        verify(passwordEncoder).encode(newPassword);
        verify(repository).save(testUser);
    }

    @Test
    void changePassword_WhenUserNotFirstLoginAndIncorrectOldPassword_ShouldThrowException() {
        String oldPassword = "wrongPassword";
        String newPassword = "newPassword123";
        com.economato.inventory.dto.request.ChangePasswordRequestDTO request = new com.economato.inventory.dto.request.ChangePasswordRequestDTO();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(false);
        testUser.setPassword("encodedOldPassword");
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(oldPassword, "encodedOldPassword")).thenReturn(false);

        assertThrows(InvalidOperationException.class, () -> userService.changePassword(1, request, false));
        verify(repository, never()).save(testUser);
    }

    @Test
    void changePassword_WhenUserNotFirstLoginAndMissingOldPassword_ShouldThrowException() {
        String newPassword = "newPassword123";
        com.economato.inventory.dto.request.ChangePasswordRequestDTO request = new com.economato.inventory.dto.request.ChangePasswordRequestDTO();
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(false);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        assertThrows(InvalidOperationException.class, () -> userService.changePassword(1, request, false));
        verify(repository, never()).save(testUser);
    }

    @Test
    void updateFirstLoginStatus_WhenAdminChangesToFalse_ShouldSucceed() {
        testUser.setFirstLogin(true);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.updateFirstLoginStatus(1, false, true);

        assertFalse(testUser.isFirstLogin());
        verify(repository).save(testUser);
    }

    @Test
    void updateFirstLoginStatus_WhenAdminChangesToTrue_ShouldSucceed() {
        testUser.setFirstLogin(false);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.updateFirstLoginStatus(1, true, true);

        assertTrue(testUser.isFirstLogin());
        verify(repository).save(testUser);
    }

    @Test
    void updateFirstLoginStatus_WhenUserChangesToFalse_ShouldSucceed() {
        testUser.setFirstLogin(true);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.updateFirstLoginStatus(1, false, false);

        assertFalse(testUser.isFirstLogin());
        verify(repository).save(testUser);
    }

    @Test
    void updateFirstLoginStatus_WhenUserTriesToReactivate_ShouldThrowException() {
        testUser.setFirstLogin(false);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        assertThrows(InvalidOperationException.class,
                () -> userService.updateFirstLoginStatus(1, true, false));
        verify(repository, never()).save(testUser);
    }

    @Test
    void updateFirstLoginStatus_WhenUserNotFound_ShouldThrowException() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateFirstLoginStatus(999, false, false));
        verify(repository, never()).save(any(User.class));
    }

    // ==================== Tests para funcionalidad de usuarios ocultos
    // ====================

    @Test
    void findHiddenUsers_ShouldReturnListOfHiddenUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        UserProjection hiddenProjection = mock(UserProjection.class);
        lenient().when(hiddenProjection.getId()).thenReturn(2);
        lenient().when(hiddenProjection.getName()).thenReturn("Hidden User");
        lenient().when(hiddenProjection.getUser()).thenReturn("hiddenUser");
        lenient().when(hiddenProjection.getIsHidden()).thenReturn(true);
        lenient().when(hiddenProjection.getRole()).thenReturn(Role.USER);

        Page<UserProjection> page = new PageImpl<>(Arrays.asList(hiddenProjection));
        when(repository.findByIsHiddenTrue(pageable)).thenReturn(page);

        UserResponseDTO hiddenResponseDTO = new UserResponseDTO();
        hiddenResponseDTO.setHidden(true);
        when(userMapper.toResponseDTO(hiddenProjection)).thenReturn(hiddenResponseDTO);

        List<UserResponseDTO> result = userService.findHiddenUsers(pageable);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isHidden());
        verify(repository).findByIsHiddenTrue(pageable);
    }

    @Test
    void findHiddenUsers_WhenNoHiddenUsers_ShouldReturnEmptyList() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserProjection> page = new PageImpl<>(Arrays.asList());
        when(repository.findByIsHiddenTrue(pageable)).thenReturn(page);

        List<UserResponseDTO> result = userService.findHiddenUsers(pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findByIsHiddenTrue(pageable);
    }

    @Test
    void toggleUserHiddenStatus_WhenHidingNormalUser_ShouldSucceed() {
        testUser.setRole(Role.USER);
        testUser.setHidden(false);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.toggleUserHiddenStatus(1, true);

        assertTrue(testUser.isHidden());
        verify(repository).findById(1);
        verify(repository).save(testUser);
    }

    @Test
    void toggleUserHiddenStatus_WhenUnhidingUser_ShouldSucceed() {
        testUser.setHidden(true);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.toggleUserHiddenStatus(1, false);

        assertFalse(testUser.isHidden());
        verify(repository).findById(1);
        verify(repository).save(testUser);
    }

    @Test
    void toggleUserHiddenStatus_WhenHidingLastVisibleAdmin_ShouldThrowException() {
        testUser.setId(1);
        testUser.setRole(Role.ADMIN);
        testUser.setHidden(false);

        UserProjection adminProjection = mock(UserProjection.class);
        lenient().when(adminProjection.getRole()).thenReturn(Role.ADMIN);

        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        Page<UserProjection> page = new PageImpl<>(Arrays.asList(adminProjection));
        when(repository.findByIsHiddenFalse(any(Pageable.class))).thenReturn(page);

        assertThrows(InvalidOperationException.class,
                () -> userService.toggleUserHiddenStatus(1, true));
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void toggleUserHiddenStatus_WhenHidingAdminButMultipleExists_ShouldSucceed() {
        testUser.setId(1);
        testUser.setRole(Role.ADMIN);
        testUser.setHidden(false);

        UserProjection admin1 = mock(UserProjection.class);
        lenient().when(admin1.getId()).thenReturn(1);
        when(admin1.getRole()).thenReturn(Role.ADMIN);

        UserProjection admin2 = mock(UserProjection.class);
        lenient().when(admin2.getId()).thenReturn(2);
        when(admin2.getRole()).thenReturn(Role.ADMIN);

        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        Page<UserProjection> page = new PageImpl<>(Arrays.asList(admin1, admin2));
        when(repository.findByIsHiddenFalse(any(Pageable.class))).thenReturn(page);
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.toggleUserHiddenStatus(1, true);

        assertTrue(testUser.isHidden());
        verify(repository).save(testUser);
    }

    @Test
    void toggleUserHiddenStatus_WhenUserNotFound_ShouldThrowException() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.toggleUserHiddenStatus(999, true));
        verify(repository, never()).save(any(User.class));
    }

    // ==================== Tests para funcionalidad de profesor (teacher)
    // ====================

    @Test
    void assignTeacher_WhenValidTeacher_ShouldAssignSuccessfully() {
        User teacher = new User();
        teacher.setId(2);
        teacher.setRole(Role.ADMIN);

        testUser.setRole(Role.USER);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.findById(2)).thenReturn(Optional.of(teacher));

        userService.assignTeacher(1, 2);

        assertEquals(teacher, testUser.getTeacher());
        verify(repository).save(testUser);
    }

    @Test
    void assignTeacher_WhenTeacherIsNull_ShouldUnassignTeacher() {
        User teacher = new User();
        teacher.setId(2);
        teacher.setRole(Role.ADMIN);
        testUser.setTeacher(teacher);
        testUser.setRole(Role.USER);

        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        userService.assignTeacher(1, null);

        assertNull(testUser.getTeacher());
        verify(repository).save(testUser);
    }

    @Test
    void assignTeacher_WhenUserIsAdmin_ShouldThrowException() {
        testUser.setRole(Role.ADMIN);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        assertThrows(InvalidOperationException.class, () -> userService.assignTeacher(1, 2));
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void assignTeacher_WhenTeacherIsNotAdmin_ShouldThrowException() {
        User invalidTeacher = new User();
        invalidTeacher.setId(2);
        invalidTeacher.setRole(Role.CHEF);

        testUser.setRole(Role.USER);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.findById(2)).thenReturn(Optional.of(invalidTeacher));

        assertThrows(InvalidOperationException.class, () -> userService.assignTeacher(1, 2));
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void getMyStudents_WhenUserIsAdmin_ShouldReturnStudents() {
        User adminTeacher = new User();
        adminTeacher.setId(1);
        adminTeacher.setName("Admin Teacher");
        adminTeacher.setUser("adminTeacher");
        adminTeacher.setRole(Role.ADMIN);

        UserProjection studentProjection = mock(UserProjection.class);
        lenient().when(studentProjection.getId()).thenReturn(2);
        lenient().when(studentProjection.getName()).thenReturn("Student User");

        when(repository.findByName("adminTeacher")).thenReturn(Optional.of(adminTeacher));
        when(repository.findProjectedByTeacherIdAndIsHiddenFalse(1)).thenReturn(Arrays.asList(studentProjection));

        UserResponseDTO studentResponseDTO = new UserResponseDTO();
        studentResponseDTO.setId(2);
        studentResponseDTO.setName("Student User");
        when(userMapper.toResponseDTO(studentProjection)).thenReturn(studentResponseDTO);

        List<UserResponseDTO> result = userService.getMyStudents("adminTeacher");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Student User", result.get(0).getName());
        verify(repository).findProjectedByTeacherIdAndIsHiddenFalse(1);
    }

    @Test
    void getMyStudents_WhenUserIsNotAdmin_ShouldThrowException() {
        User regularUser = new User();
        regularUser.setName("Regular User");
        regularUser.setUser("regularUser");
        regularUser.setRole(Role.USER);

        when(repository.findByName("regularUser")).thenReturn(Optional.of(regularUser));

        assertThrows(InvalidOperationException.class, () -> userService.getMyStudents("regularUser"));
    }

    @Test
    void escalateRole_WhenUserExistsAndDurationIsValid_ShouldEscalateAndSchedule() {
        RoleEscalationRequestDTO requestDTO = new RoleEscalationRequestDTO();
        requestDTO.setDurationMinutes(60);

        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(escalationRepository.findByUserId(1)).thenReturn(Optional.empty());

        TemporaryRoleEscalation mockEscalation = new TemporaryRoleEscalation();
        mockEscalation.setUser(testUser);
        mockEscalation.setExpirationTime(LocalDateTime.now().plusMinutes(60));

        when(escalationMapper.toEntity(any(), any())).thenReturn(mockEscalation);
        when(escalationRepository.save(any(TemporaryRoleEscalation.class))).thenAnswer(i -> i.getArgument(0));

        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
        doReturn(mockFuture).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        userService.escalateRole(1, requestDTO);

        assertEquals(Role.CHEF, testUser.getRole());
        verify(repository).save(testUser);
        verify(customUserDetailsService).evictUser("testUser");
        verify(escalationRepository).save(any(TemporaryRoleEscalation.class));
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void escalateRole_WhenUserNotFound_ShouldThrowException() {
        RoleEscalationRequestDTO requestDTO = new RoleEscalationRequestDTO();
        requestDTO.setDurationMinutes(60);

        when(repository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.escalateRole(999, requestDTO));
        verify(escalationRepository, never()).save(any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void deescalateRole_WhenUserExists_ShouldDeescalateAndCancelTask() {
        testUser.setRole(Role.CHEF);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        userService.deescalateRole(1);

        assertEquals(Role.USER, testUser.getRole());
        verify(repository).save(testUser);
        verify(escalationRepository).deleteByUserId(1);
        verify(customUserDetailsService).evictUser("testUser");
    }

    @Test
    void deescalateRole_WhenUserNotFound_ShouldThrowException() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deescalateRole(999));
        verify(escalationRepository, never()).deleteByUserId(999);
    }
}