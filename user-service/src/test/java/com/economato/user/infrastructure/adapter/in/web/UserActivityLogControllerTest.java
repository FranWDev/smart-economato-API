package com.economato.user.infrastructure.adapter.in.web;

import com.economato.user.application.dto.response.UserActivityLogResponseDTO;
import com.economato.user.application.port.in.UserActivityUseCase;
import com.economato.user.application.port.out.TokenBlacklistPort;
import com.economato.user.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import com.economato.user.infrastructure.config.security.JwtFilter;
import com.economato.user.infrastructure.config.security.JwtUtils;
import com.economato.user.infrastructure.config.security.SecurityConfig;
import com.economato.user.infrastructure.config.web.I18nService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserActivityLogController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class UserActivityLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserActivityUseCase userActivityUseCase;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private TokenBlacklistPort tokenBlacklistPort;

    @MockitoBean
    private JpaUserRepository jpaUserRepository;

    @MockitoBean
    private I18nService i18nService;

    @Test
    void getAll_AsAdmin_ShouldReturnActivityLogs() throws Exception {
        UserActivityLogResponseDTO dto = UserActivityLogResponseDTO.builder()
                .id(1L)
                .userId(1)
                .username("juanperez")
                .displayName("Juan Perez")
                .action("LOGIN")
                .timestamp(LocalDateTime.now())
                .build();

        when(userActivityUseCase.getAllActivity(any())).thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/activity").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("LOGIN"));
    }

    @Test
    void getAll_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/activity").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByUserId_AsChef_ShouldReturnUserLogs() throws Exception {
        UserActivityLogResponseDTO dto = UserActivityLogResponseDTO.builder()
                .id(1L)
                .userId(1)
                .username("student1")
                .displayName("Student One")
                .action("SCREEN_CHANGED")
                .timestamp(LocalDateTime.now())
                .build();

        when(userActivityUseCase.getActivityByUserId(any(), any(), any())).thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/activity/user/1").with(user("chef").roles("CHEF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("SCREEN_CHANGED"));
    }

    @Test
    void getByUserId_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/activity/user/1"))
                .andExpect(status().isUnauthorized());
    }
}
