package com.economato.user.infrastructure.adapter.in.web;

import com.economato.user.application.dto.request.UserRequestDTO;
import com.economato.user.application.dto.response.UserResponseDTO;
import com.economato.user.application.dto.response.UserStatsResponseDTO;
import com.economato.user.application.port.in.UserManagementUseCase;
import com.economato.user.application.port.out.TokenBlacklistPort;
import com.economato.user.domain.model.Role;
import com.economato.user.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import com.economato.user.infrastructure.config.security.JwtFilter;
import com.economato.user.infrastructure.config.security.JwtUtils;
import com.economato.user.infrastructure.config.security.SecurityConfig;
import com.economato.user.infrastructure.config.web.I18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserManagementUseCase userManagementUseCase;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private TokenBlacklistPort tokenBlacklistPort;

    @MockitoBean
    private JpaUserRepository jpaUserRepository;

    @MockitoBean
    private I18nService i18nService;

    @Test
    void getAll_AsAdmin_ShouldReturnUsersPage() throws Exception {
        UserResponseDTO userDto = new UserResponseDTO(1, "Juan Perez", "juanperez", false, false, Role.USER, null, null);
        when(userManagementUseCase.findAll(any())).thenReturn(new PageImpl<>(List.of(userDto)));

        mockMvc.perform(get("/api/v1/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Juan Perez"));
    }

    @Test
    void getAll_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_AsUser_ShouldReturnUser() throws Exception {
        UserResponseDTO userDto = new UserResponseDTO(1, "Juan Perez", "juanperez", false, false, Role.USER, null, null);
        when(userManagementUseCase.findById(1)).thenReturn(Optional.of(userDto));

        mockMvc.perform(get("/api/v1/users/1").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getMe_WhenAuthenticated_ShouldReturnCurrentUser() throws Exception {
        UserResponseDTO userDto = new UserResponseDTO(1, "Juan Perez", "juanperez", false, false, Role.USER, null, null);
        when(userManagementUseCase.findCurrentUser("juanperez")).thenReturn(userDto);

        mockMvc.perform(get("/api/v1/users/me").with(user("juanperez").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("juanperez"));
    }

    @Test
    void create_AsAdmin_ShouldReturnCreated() throws Exception {
        UserRequestDTO request = new UserRequestDTO("Carlos Gomez", "carlosg", "password123", Role.USER, null);
        UserResponseDTO response = new UserResponseDTO(2, "Carlos Gomez", "carlosg", true, false, Role.USER, null, null);

        when(userManagementUseCase.save(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/users").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Carlos Gomez"));
    }

    @Test
    void create_AsChef_ShouldReturnForbidden() throws Exception {
        UserRequestDTO request = new UserRequestDTO("Carlos Gomez", "carlosg", "password123", Role.USER, null);

        mockMvc.perform(post("/api/v1/users").with(user("chef").roles("CHEF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_AsAdmin_ShouldReturnUpdatedUser() throws Exception {
        UserRequestDTO request = new UserRequestDTO("Carlos Gomez Updated", "carlosg", "newpass123", Role.USER, null);
        UserResponseDTO response = new UserResponseDTO(2, "Carlos Gomez Updated", "carlosg", false, false, Role.USER, null, null);

        when(userManagementUseCase.update(eq(2), any())).thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/v1/users/2").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carlos Gomez Updated"));
    }

    @Test
    void delete_AsAdmin_ShouldReturnNoContent() throws Exception {
        doNothing().when(userManagementUseCase).deleteById(1);

        mockMvc.perform(delete("/api/v1/users/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void getStats_AsAdmin_ShouldReturnUserStats() throws Exception {
        Map<String, Long> roleStats = new HashMap<>();
        roleStats.put("ADMIN", 2L);
        roleStats.put("USER", 10L);

        UserStatsResponseDTO stats = new UserStatsResponseDTO(12, roleStats);
        when(userManagementUseCase.getUserStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/users/stats").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(12));
    }
}
