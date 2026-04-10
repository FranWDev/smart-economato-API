package com.economato.inventory.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;

import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.response.UserActivityLogResponseDTO;
import com.economato.inventory.application.usecase.TokenBlacklistService;
import com.economato.inventory.application.usecase.UserActivityLogService;
import com.economato.inventory.infrastructure.config.security.SecurityConfig;
import com.economato.inventory.infrastructure.config.security.JwtUtils;
import com.economato.inventory.infrastructure.config.web.I18nService;

@WebMvcTest(UserActivityLogController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class UserActivityLogControllerMockTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserActivityLogService userActivityLogService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private I18nService i18nService;

    @MockitoBean
    private LocaleResolver localeResolver;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void getAll_asAdmin_shouldReturnPaginatedResults() throws Exception {
        RestPage<UserActivityLogResponseDTO> page = new RestPage<>(List.of(sampleDto()));
        when(userActivityLogService.getAllActivity(any())).thenReturn(page);

        mockMvc.perform(get("/api/user-activity?page=0&size=20").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getAll_asUser_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/user-activity").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAll_asChef_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/user-activity").with(user("chef").roles("CHEF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByUserId_asAdmin_shouldReturnResults() throws Exception {
        when(userActivityLogService.getActivityByUserId(any(), any(), any())).thenReturn(new RestPage<>(List.of(sampleDto())));

        mockMvc.perform(get("/api/user-activity/user/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getByUserId_asChef_shouldReturnResults() throws Exception {
        when(userActivityLogService.getActivityByUserId(any(), any(), any())).thenReturn(new RestPage<>(List.of(sampleDto())));

        mockMvc.perform(get("/api/user-activity/user/1").with(user("chef").roles("CHEF")))
                .andExpect(status().isOk());
    }

    @Test
    void getMyStudentsActivity_asChef_shouldReturnResults() throws Exception {
        when(userActivityLogService.getMyStudentsActivity(any(), any())).thenReturn(new RestPage<>(List.of(sampleDto())));

        mockMvc.perform(get("/api/user-activity/my-students").with(user("chef").roles("CHEF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getMyStudentsActivity_asAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/user-activity/my-students").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByUserId_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/user-activity/user/1"))
                .andExpect(status().isUnauthorized());
    }

    private UserActivityLogResponseDTO sampleDto() {
        return UserActivityLogResponseDTO.builder()
                .id(1L)
                .userId(1)
                .username("studentUser")
                .displayName("Student")
                .action("SCREEN_CHANGED")
                .screen("DASHBOARD")
                .sessionId("s1")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
