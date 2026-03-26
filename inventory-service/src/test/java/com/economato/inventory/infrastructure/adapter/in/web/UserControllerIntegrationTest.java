package com.economato.inventory.infrastructure.adapter.in.web;

import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.application.dto.request.BatchTeacherAssignmentRequestDTO;
import com.economato.inventory.application.dto.request.LoginRequestDTO;
import com.economato.inventory.application.dto.request.RoleEscalationRequestDTO;
import com.economato.inventory.application.dto.request.TeacherAssignmentRequestDTO;
import com.economato.inventory.application.dto.request.TransferStudentsRequestDTO;
import com.economato.inventory.application.dto.request.UserRequestDTO;
import com.economato.inventory.application.dto.response.LoginResponseDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.TemporaryRoleEscalationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

class UserControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/users";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private TemporaryRoleEscalationRepository escalationRepository;

        private String jwtToken;
        private User testAdmin;

        @BeforeEach
        void setUp() throws Exception {
                escalationRepository.deleteAll();
                userRepository.deleteAll();

                testAdmin = TestDataUtil.createAdminUser();
                userRepository.saveAndFlush(testAdmin);

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(testAdmin.getName());
                loginRequest.setPassword("admin123");

                String response = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                jwtToken = loginResponse.getToken();
        }

        @Test
        void whenCreateUser_thenSuccess() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.user").value(userRequest.getUser()))
                                .andExpect(jsonPath("$.name").value(userRequest.getName()))
                                .andExpect(jsonPath("$.role").value(userRequest.getRole().name()));
        }

        @Test
        void whenGetAllUsers_thenSuccess() throws Exception {
                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", notNullValue()))
                                .andExpect(jsonPath("$.content[*].user").exists())
                                .andExpect(jsonPath("$.content[*].name").exists())
                                .andExpect(jsonPath("$.content[*].role").exists());
        }

        @Test
        void whenGetUserById_thenSuccess() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user").value(userRequest.getUser()))
                                .andExpect(jsonPath("$.name").value(userRequest.getName()))
                                .andExpect(jsonPath("$.role").value(userRequest.getRole().name()));
        }

        @Test
        void whenUpdateUser_thenSuccess() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                userRequest.setName(userRequest.getName() + " Actualizado");

                mockMvc.perform(put(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(userRequest.getName()));
        }

        @Test
        void whenUpdateUserWithoutPassword_thenSuccess() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                userRequest.setName(userRequest.getName() + " Actualizado");
                userRequest.setPassword(null); // omit password

                mockMvc.perform(put(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(userRequest.getName()));
        }

        @Test
        void whenDeleteUser_thenSuccess() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                mockMvc.perform(delete(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenCreateUserWithDuplicateEmail_thenBadRequest() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated());

                UserRequestDTO duplicateUser = new UserRequestDTO();
                duplicateUser.setName("Otro Usuario");
                duplicateUser.setUser(userRequest.getUser());
                duplicateUser.setPassword("password123");
                duplicateUser.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(duplicateUser)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenCreateUserWithDuplicateName_thenBadRequest() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated());

                UserRequestDTO duplicateUser = new UserRequestDTO();
                duplicateUser.setName(userRequest.getName());
                duplicateUser.setUser("otro@user.com");
                duplicateUser.setPassword("password123");
                duplicateUser.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(duplicateUser)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenDeleteLastAdmin_thenBadRequest() throws Exception {

                mockMvc.perform(delete(BASE_URL + "/{id}", testAdmin.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenGetUsersByRole_thenSuccess() throws Exception {

                UserRequestDTO user1 = new UserRequestDTO();
                user1.setName("Chef Usuario");
                user1.setUser("chef@test.com");
                user1.setPassword("password123");
                user1.setRole(Role.CHEF);

                UserRequestDTO user2 = new UserRequestDTO();
                user2.setName("User Usuario");
                user2.setUser("user@test.com");
                user2.setPassword("password123");
                user2.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(user1)))
                                .andExpect(status().isCreated());

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(user2)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/by-role/CHEF")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].role").value("CHEF"));

                mockMvc.perform(get(BASE_URL + "/by-role/ADMIN")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].role").value("ADMIN"));
        }

        @Test
        void whenCreateUserWithInvalidUser_thenBadRequest() throws Exception {
                UserRequestDTO userRequest = new UserRequestDTO();
                userRequest.setName("Usuario Test");
                userRequest.setUser("");
                userRequest.setPassword("password123");
                userRequest.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenCreateUserWithShortPassword_thenBadRequest() throws Exception {
                UserRequestDTO userRequest = new UserRequestDTO();
                userRequest.setName("Usuario Test");
                userRequest.setUser("test@valid.com");
                userRequest.setPassword("12345");
                userRequest.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenUpdateUserUser_WithExistingUser_thenBadRequest() throws Exception {

                UserRequestDTO user1 = TestDataUtil.createUserRequestDTO();
                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(user1)))
                                .andExpect(status().isCreated());

                UserRequestDTO user2 = new UserRequestDTO();
                user2.setName("Segundo Usuario");
                user2.setUser("segundo@test.com");
                user2.setPassword("password123");
                user2.setRole(Role.USER);

                String response2 = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(user2)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser2 = objectMapper.readValue(response2, UserResponseDTO.class);

                user2.setUser(user1.getUser());

                mockMvc.perform(put(BASE_URL + "/{id}", createdUser2.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(user2)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenUpdateFirstLoginStatus_thenSuccess() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                mockMvc.perform(patch(BASE_URL + "/{id}/first-login", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("false"))
                                .andExpect(status().isOk());

                mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.firstLogin").value(false));
        }

        @Test
        void whenAdminReactivatesFirstLogin_thenSuccess() throws Exception {
                // Crear un usuario
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                // Cambiar a false primero
                mockMvc.perform(patch(BASE_URL + "/{id}/first-login", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("false"))
                                .andExpect(status().isOk());

                // Admin puede reactivarlo a true
                mockMvc.perform(patch(BASE_URL + "/{id}/first-login", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("true"))
                                .andExpect(status().isOk());

                // Verificar que está en true
                mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.firstLogin").value(true));
        }

        @Test
        void whenUserTriesToReactivateFirstLogin_thenBadRequest() throws Exception {
                // Crear un usuario regular
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();
                userRequest.setRole(Role.USER);
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                // Cambiar a false primero
                mockMvc.perform(patch(BASE_URL + "/{id}/first-login", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("false"))
                                .andExpect(status().isOk());

                // Login como el usuario creado
                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(createdUser.getName());
                loginRequest.setPassword(userRequest.getPassword());

                String loginResponse = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO userLoginResponse = objectMapper.readValue(loginResponse, LoginResponseDTO.class);
                String userToken = userLoginResponse.getToken();

                // El usuario no debería poder reactivar firstLogin
                mockMvc.perform(patch(BASE_URL + "/{id}/first-login", createdUser.getId())
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("true"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenGetCurrentUser_thenReturnsAuthenticatedUserData() throws Exception {
                mockMvc.perform(get(BASE_URL + "/me")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(testAdmin.getName()))
                                .andExpect(jsonPath("$.role").value("ADMIN"))
                                .andExpect(jsonPath("$.id").value(testAdmin.getId()));
        }

        @Test
        void whenGetCurrentUser_withoutToken_thenUnauthorized() throws Exception {
                mockMvc.perform(get(BASE_URL + "/me"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void whenGetHiddenUsers_thenSuccess() throws Exception {
                // Crear un usuario y ocultarlo
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                // Ocultar el usuario
                mockMvc.perform(patch(BASE_URL + "/{id}/hidden", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("true"))
                                .andExpect(status().isOk());

                // Obtener usuarios ocultos
                mockMvc.perform(get(BASE_URL + "/hidden")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(greaterThan(0))))
                                .andExpect(jsonPath("$.content[0].hidden").value(true));
        }

        @Test
        void whenGetHiddenUsers_whenNoneExist_thenReturnEmptyList() throws Exception {
                mockMvc.perform(get(BASE_URL + "/hidden")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", empty()));
        }

        @Test
        void whenToggleUserHidden_thenSuccess() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                // Ocultar usuario
                mockMvc.perform(patch(BASE_URL + "/{id}/hidden", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("true"))
                                .andExpect(status().isOk());

                // Verificar que está oculto
                String getResponse = mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO updatedUser = objectMapper.readValue(getResponse, UserResponseDTO.class);
                assert updatedUser.isHidden() : "Usuario debe estar oculto";
        }

        @Test
        void whenToggleUserHidden_unhideUser_thenSuccess() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                // Ocultar usuario
                mockMvc.perform(patch(BASE_URL + "/{id}/hidden", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("true"))
                                .andExpect(status().isOk());

                // Mostrar usuario nuevamente
                mockMvc.perform(patch(BASE_URL + "/{id}/hidden", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("false"))
                                .andExpect(status().isOk());

                // Verificar que ya no está oculto
                String getResponse = mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO updatedUser = objectMapper.readValue(getResponse, UserResponseDTO.class);
                assert !updatedUser.isHidden() : "Usuario no debe estar oculto";
        }

        @Test
        void whenTryToHideLastAdmin_thenBadRequest() throws Exception {
                // Intentar ocultar el único admin
                mockMvc.perform(patch(BASE_URL + "/{id}/hidden", testAdmin.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("true"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenToggleHiddenUsersShouldNotAppearInNormalList() throws Exception {
                UserRequestDTO userRequest = TestDataUtil.createUserRequestDTO();
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                // Obtener usuarios antes de ocultar
                String beforeResponse = mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                com.fasterxml.jackson.databind.JsonNode beforeNode = objectMapper.readTree(beforeResponse);
                int countBefore = beforeNode.get("content").size();

                // Ocultar usuario
                mockMvc.perform(patch(BASE_URL + "/{id}/hidden", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("true"))
                                .andExpect(status().isOk());

                // Obtener usuarios después de ocultar
                String afterResponse = mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                com.fasterxml.jackson.databind.JsonNode afterNode = objectMapper.readTree(afterResponse);
                int countAfter = afterNode.get("content").size();

                assert countAfter == countBefore - 1 : "El usuario oculto no debe aparecer en la lista";
        }

        // ==================== Tests funcionalidad Profesor ====================

        @Test
        void whenGetTeachers_thenSuccessAndReturnsChefUsers() throws Exception {
                // Crear un usuario con rol CHEF
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                mockMvc.perform(get(BASE_URL + "/teachers")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", notNullValue()))
                                .andExpect(jsonPath("$[*].role", everyItem(is("CHEF"))));
        }

        @Test
        void whenAssignTeacher_thenSuccess() throws Exception {
                // Crear estudiante normal
                UserRequestDTO studentRequest = TestDataUtil.createUserRequestDTO();
                studentRequest.setUser("studentUser");
                studentRequest.setRole(Role.USER);
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(studentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdStudent = objectMapper.readValue(response, UserResponseDTO.class);

                // Crear profesor chef
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                // Asignar el chef profesor
                TeacherAssignmentRequestDTO assignmentRequest = new TeacherAssignmentRequestDTO(
                                chef.getId());

                mockMvc.perform(patch(BASE_URL + "/{id}/teacher", createdStudent.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(assignmentRequest)))
                                .andExpect(status().isOk());

                // Verificar que el estudiante tiene el profesor
                mockMvc.perform(get(BASE_URL + "/{id}", createdStudent.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.teacher").exists())
                                .andExpect(jsonPath("$.teacher.id").value(chef.getId()));
        }

        @Test
        void whenAssignTeacherToChef_thenBadRequest() throws Exception {
                // Crear un chef
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                TeacherAssignmentRequestDTO assignmentRequest = new TeacherAssignmentRequestDTO(
                                chef.getId());

                mockMvc.perform(patch(BASE_URL + "/{id}/teacher", chef.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(assignmentRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenGetMyStudents_thenSuccess() throws Exception {
                // Crear profesor chef
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                // Login como chef
                LoginRequestDTO chefLogin = new LoginRequestDTO();
                chefLogin.setName(chef.getName());
                chefLogin.setPassword("chef123");

                String chefTokenResponse = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(chefLogin)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                String chefToken = objectMapper.readValue(chefTokenResponse, LoginResponseDTO.class).getToken();

                // Crear estudiante
                UserRequestDTO studentRequest = TestDataUtil.createUserRequestDTO();
                studentRequest.setUser("myStudent");
                studentRequest.setRole(Role.USER);
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(studentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdStudent = objectMapper.readValue(response, UserResponseDTO.class);

                // Asignar el chef como profesor
                TeacherAssignmentRequestDTO assignmentRequest = new TeacherAssignmentRequestDTO(
                                chef.getId());
                mockMvc.perform(patch(BASE_URL + "/{id}/teacher", createdStudent.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(assignmentRequest)))
                                .andExpect(status().isOk());

                // Obtener estudiantes con el token del chef
                mockMvc.perform(get(BASE_URL + "/students")
                                .header("Authorization", "Bearer " + chefToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", notNullValue()))
                                .andExpect(jsonPath("$[*].id", hasItem(createdStudent.getId())));
        }

        @Test
        void whenEscalateRole_thenSuccess() throws Exception {
                UserRequestDTO studentRequest = TestDataUtil.createUserRequestDTO();
                studentRequest.setUser("userToEscalate");
                studentRequest.setRole(Role.USER);
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(studentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                RoleEscalationRequestDTO escalationRequest = new RoleEscalationRequestDTO();
                escalationRequest.setDurationMinutes(60);

                mockMvc.perform(post(BASE_URL + "/{id}/escalate", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(escalationRequest)))
                                .andExpect(status().isOk());

                mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.role").value(Role.ELEVATED.name()));
        }

        @Test
        void whenDeescalateRole_thenSuccess() throws Exception {
                UserRequestDTO studentRequest = TestDataUtil.createUserRequestDTO();
                studentRequest.setUser("userToDeescalate");
                studentRequest.setRole(Role.USER);
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(studentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser = objectMapper.readValue(response, UserResponseDTO.class);

                RoleEscalationRequestDTO escalationRequest = new RoleEscalationRequestDTO();
                escalationRequest.setDurationMinutes(60);

                mockMvc.perform(post(BASE_URL + "/{id}/escalate", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(escalationRequest)))
                                .andExpect(status().isOk());

                mockMvc.perform(post(BASE_URL + "/{id}/de-escalate", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk());

                mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.role").value(Role.USER.name()));
        }

        // ==================== Tests batch asignación de profesor ====================

        @Test
        void whenBatchAssignTeacher_thenSuccess() throws Exception {
                // Crear profesor chef
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                // Crear dos alumnos
                UserRequestDTO student1Request = new UserRequestDTO();
                student1Request.setName("Alumno Uno");
                student1Request.setUser("alumno1@test.com");
                student1Request.setPassword("password123");
                student1Request.setRole(Role.USER);

                UserRequestDTO student2Request = new UserRequestDTO();
                student2Request.setName("Alumno Dos");
                student2Request.setUser("alumno2@test.com");
                student2Request.setPassword("password123");
                student2Request.setRole(Role.USER);

                String resp1 = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(student1Request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String resp2 = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(student2Request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer student1Id = objectMapper.readValue(resp1,
                                com.economato.inventory.application.dto.response.UserResponseDTO.class).getId();
                Integer student2Id = objectMapper.readValue(resp2,
                                com.economato.inventory.application.dto.response.UserResponseDTO.class).getId();

                // Asignación batch
                BatchTeacherAssignmentRequestDTO batchRequest = new BatchTeacherAssignmentRequestDTO(
                                chef.getId(), java.util.List.of(student1Id, student2Id));

                mockMvc.perform(patch(BASE_URL + "/batch/teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(batchRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.processedCount").value(2))
                                .andExpect(jsonPath("$.totalCount").value(2))
                                .andExpect(jsonPath("$.failedStudentIds", empty()));

                // Verificar que ambos alumnos tienen el profesor asignado
                mockMvc.perform(get(BASE_URL + "/{id}", student1Id)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.teacher.id").value(chef.getId()));

                mockMvc.perform(get(BASE_URL + "/{id}", student2Id)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.teacher.id").value(chef.getId()));
        }

        @Test
        void whenBatchUnassignTeacher_thenSuccess() throws Exception {
                // Crear profesor chef
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                // Crear dos alumnos ya asignados al chef
                UserRequestDTO student1Request = new UserRequestDTO();
                student1Request.setName("Alumno Unassign Uno");
                student1Request.setUser("unassign1@test.com");
                student1Request.setPassword("password123");
                student1Request.setRole(Role.USER);
                student1Request.setTeacherId(chef.getId());

                UserRequestDTO student2Request = new UserRequestDTO();
                student2Request.setName("Alumno Unassign Dos");
                student2Request.setUser("unassign2@test.com");
                student2Request.setPassword("password123");
                student2Request.setRole(Role.USER);
                student2Request.setTeacherId(chef.getId());

                String resp1 = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(student1Request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String resp2 = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(student2Request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer student1Id = objectMapper.readValue(resp1,
                                com.economato.inventory.application.dto.response.UserResponseDTO.class).getId();
                Integer student2Id = objectMapper.readValue(resp2,
                                com.economato.inventory.application.dto.response.UserResponseDTO.class).getId();

                // Desasignar en batch (teacherId = null)
                BatchTeacherAssignmentRequestDTO batchRequest = new BatchTeacherAssignmentRequestDTO(
                                null, java.util.List.of(student1Id, student2Id));

                mockMvc.perform(patch(BASE_URL + "/batch/teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(batchRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.processedCount").value(2))
                                .andExpect(jsonPath("$.totalCount").value(2));

                // Verificar que los alumnos ya no tienen profesor
                mockMvc.perform(get(BASE_URL + "/{id}", student1Id)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.teacher").doesNotExist());

                mockMvc.perform(get(BASE_URL + "/{id}", student2Id)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.teacher").doesNotExist());
        }

        @Test
        void whenBatchAssignTeacher_withNonExistentStudent_thenNotFound() throws Exception {
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                BatchTeacherAssignmentRequestDTO batchRequest = new BatchTeacherAssignmentRequestDTO(
                                chef.getId(), java.util.List.of(99999));

                mockMvc.perform(patch(BASE_URL + "/batch/teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(batchRequest)))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenBatchAssignTeacher_withNonExistentTeacher_thenNotFound() throws Exception {
                UserRequestDTO studentRequest = new UserRequestDTO();
                studentRequest.setName("Alumno Test Batch");
                studentRequest.setUser("batchstudent@test.com");
                studentRequest.setPassword("password123");
                studentRequest.setRole(Role.USER);

                String resp = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(studentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer studentId = objectMapper.readValue(resp,
                                com.economato.inventory.application.dto.response.UserResponseDTO.class).getId();

                BatchTeacherAssignmentRequestDTO batchRequest = new BatchTeacherAssignmentRequestDTO(
                                99999, java.util.List.of(studentId));

                mockMvc.perform(patch(BASE_URL + "/batch/teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(batchRequest)))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenBatchAssignTeacher_toChef_thenBadRequest() throws Exception {
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                User chef2 = TestDataUtil.createUser("Chef Dos", "chef2@test.com", "chef123", Role.CHEF);
                userRepository.saveAndFlush(chef2);

                BatchTeacherAssignmentRequestDTO batchRequest = new BatchTeacherAssignmentRequestDTO(
                                chef.getId(), java.util.List.of(chef2.getId()));

                mockMvc.perform(patch(BASE_URL + "/batch/teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(batchRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenBatchAssignTeacher_withUserAsTeacher_thenBadRequest() throws Exception {
                UserRequestDTO studentRequest = new UserRequestDTO();
                studentRequest.setName("Alumno Batch Bad");
                studentRequest.setUser("batchbad@test.com");
                studentRequest.setPassword("password123");
                studentRequest.setRole(Role.USER);

                String studentResp = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(studentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer studentId = objectMapper.readValue(studentResp,
                                com.economato.inventory.application.dto.response.UserResponseDTO.class).getId();

                // Intentar usar un USER como profesor
                BatchTeacherAssignmentRequestDTO batchRequest = new BatchTeacherAssignmentRequestDTO(
                                studentId, java.util.List.of(studentId));

                mockMvc.perform(patch(BASE_URL + "/batch/teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(batchRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenBatchAssignTeacher_withEmptyStudentList_thenBadRequest() throws Exception {
                User chef = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(chef);

                BatchTeacherAssignmentRequestDTO batchRequest = new BatchTeacherAssignmentRequestDTO(
                                chef.getId(), java.util.List.of());

                mockMvc.perform(patch(BASE_URL + "/batch/teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(batchRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenBatchAssignTeacher_withoutAdminRole_thenForbidden() throws Exception {
                // Crear usuario normal y obtener su token
                UserRequestDTO userRequest = new UserRequestDTO();
                userRequest.setName("Usuario Normal Batch");
                userRequest.setUser("normalbatch@test.com");
                userRequest.setPassword("password123");
                userRequest.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andExpect(status().isCreated());

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(userRequest.getName());
                loginRequest.setPassword("password123");

                String loginResp = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                String userToken = objectMapper.readValue(loginResp, LoginResponseDTO.class).getToken();

                User chef = TestDataUtil.createUser("Chef Batch Auth", "chefbatchauth@test.com", "chef123", Role.CHEF);
                userRepository.saveAndFlush(chef);

                BatchTeacherAssignmentRequestDTO batchRequest = new BatchTeacherAssignmentRequestDTO(
                                chef.getId(), java.util.List.of(1));

                mockMvc.perform(patch(BASE_URL + "/batch/teacher")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(batchRequest)))
                                .andExpect(status().isForbidden());
        }

        @Test
        void whenGetUnassignedStudents_thenSuccess() throws Exception {
                // Crear un alumno sin profesor
                User unassigned = TestDataUtil.createUser("Unassigned", "unassigned_student", "pass", Role.USER);
                unassigned.setTeacher(null);
                unassigned.setHidden(false);
                userRepository.saveAndFlush(unassigned);

                mockMvc.perform(get(BASE_URL + "/students/unassigned")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                                .andExpect(jsonPath("$[*].id", hasItem(unassigned.getId())));
        }

        @Test
        void whenHideAllStudentsOfTeacher_thenSuccess() throws Exception {
                // Crear un profesor y sus alumnos
                User teacher = TestDataUtil.createUser("Teacher To Hide", "teacher_to_hide", "pass", Role.CHEF);
                userRepository.saveAndFlush(teacher);

                User student = TestDataUtil.createUser("Student To Hide", "student_to_hide", "pass", Role.USER);
                student.setTeacher(teacher);
                student.setHidden(false);
                userRepository.saveAndFlush(student);

                mockMvc.perform(patch(BASE_URL + "/teachers/" + teacher.getId() + "/students/hidden")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(true)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.processedCount").value(1));

                User updatedStudent = userRepository.findById(student.getId()).get();
                assertTrue(updatedStudent.isHidden());
        }

        @Test
        void whenHideAllStudentsOfTeacher_TeacherNotFound_thenNotFound() throws Exception {
                mockMvc.perform(patch(BASE_URL + "/teachers/9999/students/hidden")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(true)))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenHideAllStudentsOfTeacher_NotTeacher_thenBadRequest() throws Exception {
                User notTeacher = TestDataUtil.createUser("Not Teacher", "not_teacher", "pass", Role.USER);
                userRepository.saveAndFlush(notTeacher);

                mockMvc.perform(patch(BASE_URL + "/teachers/" + notTeacher.getId() + "/students/hidden")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(true)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenTransferStudentsBatch_thenSuccess() throws Exception {
                User fromTeacher = TestDataUtil.createUser("From Teacher", "from_teacher", "pass", Role.CHEF);
                userRepository.saveAndFlush(fromTeacher);

                User toTeacher = TestDataUtil.createUser("To Teacher", "to_teacher", "pass", Role.CHEF);
                userRepository.saveAndFlush(toTeacher);

                User student = TestDataUtil.createUser("Student To Transfer", "student_to_transfer", "pass", Role.USER);
                student.setTeacher(fromTeacher);
                userRepository.saveAndFlush(student);

                TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(
                                fromTeacher.getId(), toTeacher.getId(), List.of(student.getId()));

                mockMvc.perform(patch(BASE_URL + "/batch/transfer-teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.processedCount").value(1));

                User updatedStudent = userRepository.findById(student.getId()).get();
                assertEquals(toTeacher.getId(), updatedStudent.getTeacher().getId());
        }

        @Test
        void whenTransferStudentsBatch_SameTeacher_thenBadRequest() throws Exception {
                TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(1, 1, List.of(2));

                mockMvc.perform(patch(BASE_URL + "/batch/transfer-teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenTransferStudentsBatch_TeacherNotFound_thenNotFound() throws Exception {
                TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(1, 9999, List.of(2));

                mockMvc.perform(patch(BASE_URL + "/batch/transfer-teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(request)))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenTransferStudentsBatch_StudentNotBelongsToTeacher_thenBadRequest() throws Exception {
                User fromTeacher = TestDataUtil.createUser("FT", "ft", "pass", Role.CHEF);
                userRepository.saveAndFlush(fromTeacher);

                User toTeacher = TestDataUtil.createUser("TT", "tt", "pass", Role.CHEF);
                userRepository.saveAndFlush(toTeacher);

                User student = TestDataUtil.createUser("ST", "st", "pass", Role.USER);
                student.setTeacher(null); // No pertenece al fromTeacher
                userRepository.saveAndFlush(student);

                TransferStudentsRequestDTO request = new TransferStudentsRequestDTO(
                                fromTeacher.getId(), toTeacher.getId(), List.of(student.getId()));

                mockMvc.perform(patch(BASE_URL + "/batch/transfer-teacher")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenTransferAllStudents_thenSuccess() throws Exception {
                User fromTeacher = TestDataUtil.createUser("FT ALL", "ft_all", "pass", Role.CHEF);
                userRepository.saveAndFlush(fromTeacher);

                User toTeacher = TestDataUtil.createUser("TT ALL", "tt_all", "pass", Role.CHEF);
                userRepository.saveAndFlush(toTeacher);

                User student = TestDataUtil.createUser("ST ALL", "st_all", "pass", Role.USER);
                student.setTeacher(fromTeacher);
                userRepository.saveAndFlush(student);

                mockMvc.perform(patch(BASE_URL + "/teachers/" + fromTeacher.getId() + "/transfer-all/" + toTeacher.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.processedCount").value(1));

                User updatedStudent = userRepository.findById(student.getId()).get();
                assertEquals(toTeacher.getId(), updatedStudent.getTeacher().getId());
        }

        @Test
        void whenTransferAllStudents_SameTeacher_thenBadRequest() throws Exception {
                mockMvc.perform(patch(BASE_URL + "/teachers/1/transfer-all/1")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenTransferAllStudents_TeacherNotFound_thenNotFound() throws Exception {
                mockMvc.perform(patch(BASE_URL + "/teachers/1/transfer-all/9999")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }
}
