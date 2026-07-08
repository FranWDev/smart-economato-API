package com.economato.inventory.application.usecase.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import com.economato.inventory.application.dto.user.response.UserActivityLogResponseDTO;
import com.economato.inventory.application.mapper.user.UserActivityLogMapper;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.user.UserActivityLog;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserActivityLogRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

@ExtendWith(MockitoExtension.class)
class UserActivityLogServiceTest {

    @Mock
    private UserActivityLogRepository userActivityLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserActivityLogMapper userActivityLogMapper;
    @Mock
    private I18nService i18nService;

    @InjectMocks
    private UserActivityLogService userActivityLogService;

    private User admin;
    private User chef;
    private User student;
    private User anotherStudent;
    private UserActivityLog log;
    private UserActivityLogResponseDTO dto;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1);
        admin.setName("Admin");
        admin.setRole(Role.ADMIN);

        chef = new User();
        chef.setId(2);
        chef.setName("Chef");
        chef.setRole(Role.CHEF);

        student = new User();
        student.setId(3);
        student.setName("Student");
        student.setRole(Role.USER);
        student.setTeacher(chef);

        User otherChef = new User();
        otherChef.setId(99);
        otherChef.setRole(Role.CHEF);

        anotherStudent = new User();
        anotherStudent.setId(4);
        anotherStudent.setName("Another Student");
        anotherStudent.setRole(Role.USER);
        anotherStudent.setTeacher(otherChef);

        log = new UserActivityLog();
        log.setId(100L);
        log.setUser(student);
        log.setAction("SCREEN_CHANGED");
        log.setTimestamp(LocalDateTime.now());

        dto = UserActivityLogResponseDTO.builder()
                .id(100L)
                .userId(3)
                .action("SCREEN_CHANGED")
                .build();

        lenient().when(userActivityLogMapper.toResponseDTO(any(UserActivityLog.class))).thenReturn(dto);
    }

    @Test
    void getAllActivity_returnsPagedResults() {
        Page<UserActivityLog> page = new PageImpl<>(List.of(log));
        when(userActivityLogRepository.findAllByOrderByTimestampDesc(any())).thenReturn(page);

        Page<UserActivityLogResponseDTO> result = userActivityLogService.getAllActivity(PageRequest.of(0, 20));

        assertEquals(1, result.getContent().size());
        verify(userActivityLogRepository).findAllByOrderByTimestampDesc(any());
    }

    @Test
    void getActivityByUserId_asAdmin_returnsResults() {
        when(userRepository.findByName("admin")).thenReturn(Optional.of(admin));
        when(userActivityLogRepository.findByUserIdOrderByTimestampDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<UserActivityLogResponseDTO> result = userActivityLogService.getActivityByUserId(3, "admin",
                PageRequest.of(0, 20));

        assertEquals(1, result.getContent().size());
    }

    @Test
    void getActivityByUserId_asChef_ownStudent_returnsResults() {
        when(userRepository.findByName("chef")).thenReturn(Optional.of(chef));
        when(userRepository.findById(3)).thenReturn(Optional.of(student));
        when(userActivityLogRepository.findByUserIdOrderByTimestampDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<UserActivityLogResponseDTO> result = userActivityLogService.getActivityByUserId(3, "chef",
                PageRequest.of(0, 20));

        assertEquals(1, result.getContent().size());
    }

    @Test
    void getActivityByUserId_asChef_notOwnStudent_throwsException() {
        when(userRepository.findByName("chef")).thenReturn(Optional.of(chef));
        when(userRepository.findById(4)).thenReturn(Optional.of(anotherStudent));
        when(i18nService.getMessage(MessageKey.ERROR_AUTH_FORBIDDEN)).thenReturn("Forbidden");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                () -> userActivityLogService.getActivityByUserId(4, "chef", PageRequest.of(0, 20)));
        assertEquals("Forbidden", exception.getMessage());

        verify(userActivityLogRepository, never()).findByUserIdOrderByTimestampDesc(any(), any());
    }

    @Test
    void getMyStudentsActivity_returnsFilteredResults() {
        when(userRepository.findByName("chef")).thenReturn(Optional.of(chef));
        when(userRepository.findByTeacherId(2)).thenReturn(List.of(student));
        when(userActivityLogRepository.findByUserIdInOrderByTimestampDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<UserActivityLogResponseDTO> result = userActivityLogService.getMyStudentsActivity("chef",
                PageRequest.of(0, 20));

        assertEquals(1, result.getContent().size());
    }

    @Test
    void getMyStudentsActivity_noStudents_returnsEmptyPage() {
        when(userRepository.findByName("chef")).thenReturn(Optional.of(chef));
        when(userRepository.findByTeacherId(2)).thenReturn(List.of());

        Page<UserActivityLogResponseDTO> result = userActivityLogService.getMyStudentsActivity("chef",
                PageRequest.of(0, 20));

        assertEquals(0, result.getContent().size());
        verify(userActivityLogRepository, never()).findByUserIdInOrderByTimestampDesc(any(), any());
    }
}
