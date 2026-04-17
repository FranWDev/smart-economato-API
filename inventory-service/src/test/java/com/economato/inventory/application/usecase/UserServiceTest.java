package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.web.I18nService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.economato.inventory.application.dto.projection.RoleCountProjection;
import com.economato.inventory.application.dto.projection.UserProjection;
import com.economato.inventory.application.dto.request.TransferStudentsRequestDTO;
import com.economato.inventory.application.dto.request.UserRequestDTO;
import com.economato.inventory.application.dto.request.ChangePasswordRequestDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.application.dto.response.UserStatsResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.application.mapper.UserMapper;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.TemporaryRoleEscalationRepository;
import com.economato.inventory.domain.model.TemporaryRoleEscalation;
import com.economato.inventory.application.mapper.StatsMapper;
import com.economato.inventory.application.mapper.TemporaryRoleEscalationMapper;
import com.economato.inventory.application.dto.request.RoleEscalationRequestDTO;
import org.springframework.scheduling.TaskScheduler;
import java.time.LocalDateTime;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StatsMapper statsMapper;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private TemporaryRoleEscalationRepository escalationRepository;

    @Mock
    private TemporaryRoleEscalationMapper escalationMapper;

    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private I18nService i18nService;

    @Mock
    private RoleNotificationService roleNotificationService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserRequestDTO testUserRequestDTO;
    private UserResponseDTO testUserResponseDTO;
    private UserProjection testProjection;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
        Mockito.lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                Object arg = invocation.getArgument(1);
                String argsStr = arg instanceof Object[] ? java.util.Arrays.toString((Object[]) arg) : String.valueOf(arg);
                return ((MessageKey) invocation.getArgument(0)).name() + " " + (argsStr != null ? argsStr : "[]");
            });
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
    void findAll_ShouldReturnPageOfUsers() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<UserProjection> page = new PageImpl<>(Arrays.asList(testProjection));
        when(repository.findByIsHiddenFalse(pageable)).thenReturn(page);
        when(userMapper.toResponseDTO(any(UserProjection.class))).thenReturn(testUserResponseDTO);

        Page<UserResponseDTO> result = userService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(testUserResponseDTO.getUser(), result.getContent().get(0).getUser());
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
        doNothing().when(repository).delete(testUser);

        userService.deleteById(1);

        verify(repository).findById(1);
        verify(repository).delete(testUser);
    }

    @Test
    void findByRole_ShouldReturnUsersWithRole() {

        List<UserProjection> users = Arrays.asList(testProjection);
        when(repository.findProjectedByRoleInAndIsHiddenFalse(List.of(Role.USER, Role.ELEVATED))).thenReturn(users);
        when(userMapper.toResponseDTO(any(UserProjection.class))).thenReturn(testUserResponseDTO);

        List<UserResponseDTO> result = userService.findByRole(Role.USER);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUserResponseDTO.getRole(), result.get(0).getRole());
        verify(repository).findProjectedByRoleInAndIsHiddenFalse(List.of(Role.USER, Role.ELEVATED));
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
        verify(repository, never()).delete(any(User.class));
    }

    @Test
    void deleteById_WhenNotLastAdmin_ShouldSucceed() {
        testUser.setRole(Role.ADMIN);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.countByRole(Role.ADMIN)).thenReturn(2L);
        doNothing().when(repository).delete(testUser);

        userService.deleteById(1);

        verify(repository).delete(testUser);
    }

    @Test
    void deleteById_WhenUserNotFound_ShouldThrowException() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteById(999));
        verify(repository, never()).delete(any(User.class));
    }

    @Test
    void changePassword_WhenAdmin_ShouldUpdatePassword() {
        String newPassword = "newPassword123";
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(true);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(1, request, true, true);

        verify(passwordEncoder).encode(newPassword);
        verify(repository).save(testUser);
        assertFalse(testUser.isFirstLogin());
    }

    @Test
    void changePassword_WhenAdminChangesAnotherUser_ShouldNotUpdateFirstLogin() {
        String newPassword = "newPassword123";
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(true);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(1, request, true, false);

        verify(passwordEncoder).encode(newPassword);
        verify(repository).save(testUser);
        assertTrue(testUser.isFirstLogin());
    }

    @Test
    void changePassword_WhenUserFirstLogin_ShouldUpdatePasswordAndSetFirstLoginFalse() {
        String newPassword = "newPassword123";
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(true);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(1, request, false, false);

        verify(passwordEncoder).encode(newPassword);
        verify(repository).save(testUser);
        assertFalse(testUser.isFirstLogin());
    }

    @Test
    void changePassword_WhenUserNotFirstLoginAndCorrectOldPassword_ShouldUpdatePassword() {
        String oldPassword = "oldPassword";
        String newPassword = "newPassword123";
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(false);
        testUser.setPassword("encodedOldPassword");
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(oldPassword, "encodedOldPassword")).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(1, request, false, false);

        verify(passwordEncoder).matches(oldPassword, "encodedOldPassword");
        verify(passwordEncoder).encode(newPassword);
        verify(repository).save(testUser);
    }

    @Test
    void changePassword_WhenUserNotFirstLoginAndIncorrectOldPassword_ShouldThrowException() {
        String oldPassword = "wrongPassword";
        String newPassword = "newPassword123";
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(false);
        testUser.setPassword("encodedOldPassword");
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(oldPassword, "encodedOldPassword")).thenReturn(false);

        assertThrows(InvalidOperationException.class, () -> userService.changePassword(1, request, false, false));
        verify(repository, never()).save(testUser);
    }

    @Test
    void changePassword_WhenUserNotFirstLoginAndMissingOldPassword_ShouldThrowException() {
        String newPassword = "newPassword123";
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setNewPassword(newPassword);

        testUser.setFirstLogin(false);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        assertThrows(InvalidOperationException.class, () -> userService.changePassword(1, request, false, false));
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

        Page<UserResponseDTO> result = userService.findHiddenUsers(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertTrue(result.getContent().get(0).isHidden());
        verify(repository).findByIsHiddenTrue(pageable);
    }

    @Test
    void findHiddenUsers_WhenNoHiddenUsers_ShouldReturnEmptyList() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserProjection> page = new PageImpl<>(Arrays.asList());
        when(repository.findByIsHiddenTrue(pageable)).thenReturn(page);

        Page<UserResponseDTO> result = userService.findHiddenUsers(pageable);

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
        when(repository.countByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(1L);

        InvalidOperationException exception = assertThrows(InvalidOperationException.class,
                () -> userService.toggleUserHiddenStatus(1, true));
        assertTrue(exception.getMessage().contains(MessageKey.ERROR_USER_HIDE_LAST_ADMIN.name()));
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void toggleUserHiddenStatus_WhenHidingAdminButMultipleExists_ShouldSucceed() {
        testUser.setId(1);
        testUser.setRole(Role.ADMIN);
        testUser.setHidden(false);

        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.countByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(2L);
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
        teacher.setRole(Role.CHEF);

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
        teacher.setRole(Role.CHEF);
        testUser.setTeacher(teacher);
        testUser.setRole(Role.USER);

        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        userService.assignTeacher(1, null);

        assertNull(testUser.getTeacher());
        verify(repository).save(testUser);
    }

    @Test
    void assignTeacher_WhenUserIsChef_ShouldThrowException() {
        testUser.setRole(Role.CHEF);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        assertThrows(InvalidOperationException.class, () -> userService.assignTeacher(1, 2));
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void assignTeacher_WhenTeacherIsNotChef_ShouldThrowException() {
        User invalidTeacher = new User();
        invalidTeacher.setId(2);
        invalidTeacher.setRole(Role.USER);

        testUser.setRole(Role.USER);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));
        when(repository.findById(2)).thenReturn(Optional.of(invalidTeacher));

        assertThrows(InvalidOperationException.class, () -> userService.assignTeacher(1, 2));
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void getMyStudents_WhenUserIsChef_ShouldReturnStudents() {
        User chefTeacher = new User();
        chefTeacher.setId(1);
        chefTeacher.setName("Chef Teacher");
        chefTeacher.setUser("chefTeacher");
        chefTeacher.setRole(Role.CHEF);

        UserProjection studentProjection = mock(UserProjection.class);
        lenient().when(studentProjection.getId()).thenReturn(2);
        lenient().when(studentProjection.getName()).thenReturn("Student User");

        when(repository.findByName("chefTeacher")).thenReturn(Optional.of(chefTeacher));
        when(repository.findProjectedByTeacherIdAndIsHiddenFalse(1)).thenReturn(Arrays.asList(studentProjection));

        UserResponseDTO studentResponseDTO = new UserResponseDTO();
        studentResponseDTO.setId(2);
        studentResponseDTO.setName("Student User");
        when(userMapper.toResponseDTO(studentProjection)).thenReturn(studentResponseDTO);

        List<UserResponseDTO> result = userService.getMyStudents("chefTeacher");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Student User", result.get(0).getName());
        verify(repository).findProjectedByTeacherIdAndIsHiddenFalse(1);
    }

    @Test
    void getMyStudents_WhenUserIsNotChef_ShouldThrowException() {
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

        userService.escalateRole(1, requestDTO);

        assertEquals(Role.ELEVATED, testUser.getRole());
        verify(repository).save(testUser);
        verify(customUserDetailsService).evictUser("testUser");
        verify(escalationRepository).save(any(TemporaryRoleEscalation.class));

        ArgumentCaptor<RoleNotificationMessage> notificationCaptor = ArgumentCaptor.forClass(RoleNotificationMessage.class);
        verify(roleNotificationService).sendNotificationToUser(eq("Test User"), notificationCaptor.capture());

        RoleNotificationMessage sentNotification = notificationCaptor.getValue();
        assertEquals(AlertCode.ROLE_ESCALATION_CHANGED, sentNotification.getCode());
        assertEquals("ELEVATED", sentNotification.getNewRole());
        assertEquals("MANUAL_GRANTED", sentNotification.getReason());
        assertNotNull(sentNotification.getTimestamp());
    }

    @Test
    void escalateRole_WhenUserNotFound_ShouldThrowException() {
        RoleEscalationRequestDTO requestDTO = new RoleEscalationRequestDTO();
        requestDTO.setDurationMinutes(60);

        when(repository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.escalateRole(999, requestDTO));
        verify(escalationRepository, never()).save(any());
    }

    @Test
    void deescalateRole_WhenUserExists_ShouldDeescalateAndCancelTask() {
        testUser.setRole(Role.ELEVATED);
        when(repository.findById(1)).thenReturn(Optional.of(testUser));

        userService.deescalateRole(1);

        assertEquals(Role.USER, testUser.getRole());
        verify(repository).save(testUser);
        verify(escalationRepository).deleteByUserId(1);
        verify(customUserDetailsService).evictUser("testUser");

        ArgumentCaptor<RoleNotificationMessage> notificationCaptor = ArgumentCaptor.forClass(RoleNotificationMessage.class);
        verify(roleNotificationService).sendNotificationToUser(eq("Test User"), notificationCaptor.capture());

        RoleNotificationMessage sentNotification = notificationCaptor.getValue();
        assertEquals(AlertCode.ROLE_ESCALATION_CHANGED, sentNotification.getCode());
        assertEquals("USER", sentNotification.getNewRole());
        assertEquals("MANUAL_REVOKED", sentNotification.getReason());
        assertNotNull(sentNotification.getTimestamp());
    }

    @Test
    void deescalateRole_WhenUserNotFound_ShouldThrowException() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deescalateRole(999));
        verify(escalationRepository, never()).deleteByUserId(999);
    }

    @Test
    void getUserStats_ShouldReturnStats() {
        // Arrange
        when(repository.count()).thenReturn(10L);
        var counts = List.of(mock(RoleCountProjection.class));
        when(repository.countUsersByRole()).thenReturn(counts);

        UserStatsResponseDTO expected = UserStatsResponseDTO.builder()
                .totalUsers(10L)
                .usersByRole(java.util.Map.of("ADMIN", 1L))
                .build();

        when(statsMapper.toUserStatsDTO(anyLong(), anyList())).thenReturn(expected);

        // Act
        UserStatsResponseDTO result = userService.getUserStats();

        // Assert
        assertEquals(expected, result);
        verify(repository).count();
        verify(repository).countUsersByRole();
    }

    @Test
    void getStudentsWithoutTeacher_Success() {
        // Arrange
        UserProjection proj = mock(UserProjection.class);
        when(repository.findProjectedByTeacherIsNullAndRoleInAndIsHiddenFalse(anyList()))
                .thenReturn(List.of(proj));
        when(userMapper.toResponseDTO(any(UserProjection.class))).thenReturn(new UserResponseDTO());

        // Act
        List<UserResponseDTO> result = userService.getStudentsWithoutTeacher();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findProjectedByTeacherIsNullAndRoleInAndIsHiddenFalse(anyList());
    }

    @Test
    void hideAllStudentsOfTeacher_Success() {
        // Arrange
        User teacher = new User();
        teacher.setId(2);
        teacher.setRole(Role.CHEF);
        User student = new User();
        student.setId(3);
        student.setHidden(false);

        when(repository.findById(2)).thenReturn(Optional.of(teacher));
        when(repository.findByTeacherId(2)).thenReturn(List.of(student));

        // Act
        var result = userService.hideAllStudentsOfTeacher(2, true);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(1, result.getProcessedCount());
        assertTrue(student.isHidden());
        verify(repository).saveAll(anyList());
    }

    @Test
    void hideAllStudentsOfTeacher_NotFound() {
        // Arrange
        when(repository.findById(2)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> userService.hideAllStudentsOfTeacher(2, true));
    }

    @Test
    void hideAllStudentsOfTeacher_NotChef() {
        // Arrange
        User user = new User();
        user.setId(2);
        user.setRole(Role.USER);
        when(repository.findById(2)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> userService.hideAllStudentsOfTeacher(2, true));
    }

    @Test
    void transferStudents_Success() {
        // Arrange
        User fromTeacher = new User();
        fromTeacher.setId(2);
        fromTeacher.setRole(Role.CHEF);
        User toTeacher = new User();
        toTeacher.setId(5);
        toTeacher.setRole(Role.CHEF);
        User student = new User();
        student.setId(3);
        student.setTeacher(fromTeacher);

        TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(2, 5, List.of(3));

        when(repository.findById(2)).thenReturn(Optional.of(fromTeacher));
        when(repository.findById(5)).thenReturn(Optional.of(toTeacher));
        when(repository.findAllById(anyList())).thenReturn(List.of(student));

        // Act
        var result = userService.transferStudents(request);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(1, result.getProcessedCount());
        assertEquals(toTeacher, student.getTeacher());
        verify(repository).saveAll(anyList());
    }

    @Test
    void transferStudents_SameTeacher() {
        // Arrange
        TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(2, 2, List.of(3));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> userService.transferStudents(request));
    }

    @Test
    void transferStudents_NotFound() {
        // Arrange
        TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(2, 5, List.of(3));
        when(repository.findById(2)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> userService.transferStudents(request));
    }

    @Test
    void transferStudents_NotChef() {
        // Arrange
        User fromTeacher = new User();
        fromTeacher.setId(2);
        fromTeacher.setRole(Role.USER);
        TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(2, 5, List.of(3));

        User toTeacher = new User();
        toTeacher.setId(5);
        toTeacher.setRole(Role.CHEF);
        when(repository.findById(2)).thenReturn(Optional.of(fromTeacher));
        when(repository.findById(5)).thenReturn(Optional.of(toTeacher));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> userService.transferStudents(request));
    }

    @Test
    void transferStudents_NotBelongsToTeacher() {
        // Arrange
        User fromTeacher = new User();
        fromTeacher.setId(2);
        fromTeacher.setRole(Role.CHEF);
        User otherTeacher = new User();
        otherTeacher.setId(9);
        User student = new User();
        student.setId(3);
        student.setTeacher(otherTeacher);

        TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(2, 5, List.of(3));

        when(repository.findById(2)).thenReturn(Optional.of(fromTeacher));
        when(repository.findById(5)).thenReturn(Optional.of(new User() {{ setRole(Role.CHEF); }}));
        when(repository.findAllById(anyList())).thenReturn(List.of(student));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> userService.transferStudents(request));
    }

    @Test
    void transferAllStudents_Success() {
        // Arrange
        User fromTeacher = new User();
        fromTeacher.setId(2);
        fromTeacher.setRole(Role.CHEF);
        User toTeacher = new User();
        toTeacher.setId(5);
        toTeacher.setRole(Role.CHEF);
        User student = new User();
        student.setId(3);
        student.setTeacher(fromTeacher);

        when(repository.findById(2)).thenReturn(Optional.of(fromTeacher));
        when(repository.findById(5)).thenReturn(Optional.of(toTeacher));
        when(repository.findByTeacherId(2)).thenReturn(List.of(student));

        // Act
        var result = userService.transferAllStudents(2, 5);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(1, result.getProcessedCount());
        assertEquals(toTeacher, student.getTeacher());
        verify(repository).saveAll(anyList());
    }

    @Test
    void transferAllStudents_SameTeacher() {
        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> userService.transferAllStudents(2, 2));
    }

    @Test
    void transferAllStudents_NotFound() {
        // Arrange
        when(repository.findById(2)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> userService.transferAllStudents(2, 5));
    }
}