package com.economato.inventory.controller;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.UserRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;
import com.economato.inventory.dto.request.RoleEscalationRequestDTO;
import com.economato.inventory.repository.TemporaryRoleEscalationRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

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
                String hiddenResponse = mockMvc.perform(get(BASE_URL + "/hidden")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO[] hiddenUsers = objectMapper.readValue(hiddenResponse, UserResponseDTO[].class);
                assert hiddenUsers.length > 0 : "Debe haber al menos un usuario oculto";
                assert hiddenUsers[0].isHidden() : "El usuario debe estar marcado como oculto";
        }

        @Test
        void whenGetHiddenUsers_whenNoneExist_thenReturnEmptyList() throws Exception {
                mockMvc.perform(get(BASE_URL + "/hidden")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", empty()));
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
                com.economato.inventory.dto.request.TeacherAssignmentRequestDTO assignmentRequest = new com.economato.inventory.dto.request.TeacherAssignmentRequestDTO(
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

                com.economato.inventory.dto.request.TeacherAssignmentRequestDTO assignmentRequest = new com.economato.inventory.dto.request.TeacherAssignmentRequestDTO(
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
                com.economato.inventory.dto.request.TeacherAssignmentRequestDTO assignmentRequest = new com.economato.inventory.dto.request.TeacherAssignmentRequestDTO(
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
}
