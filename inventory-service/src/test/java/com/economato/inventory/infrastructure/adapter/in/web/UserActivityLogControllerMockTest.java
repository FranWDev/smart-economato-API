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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.response.UserActivityLogResponseDTO;
import com.economato.inventory.application.usecase.UserActivityLogService;

class UserActivityLogControllerMockTest extends BaseControllerMockTest {

    @MockitoBean
    private UserActivityLogService userActivityLogService;

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
