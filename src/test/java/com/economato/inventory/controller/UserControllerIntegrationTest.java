package com.economato.inventory.controller;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.UserRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

class UserControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/users";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private UserRepository userRepository;

        private String jwtToken;
        private User testAdmin;

        @BeforeEach
        void setUp() throws Exception {
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
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.email").value(userRequest.getEmail()))
                                .andExpect(jsonPath("$.name").value(userRequest.getName()))
                                .andExpect(jsonPath("$.role").value(userRequest.getRole().name()));
        }

        @Test
        void whenGetAllUsers_thenSuccess() throws Exception {
                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", notNullValue()))
                                .andExpect(jsonPath("$[*].email").exists())
                                .andExpect(jsonPath("$[*].name").exists())
                                .andExpect(jsonPath("$[*].role").exists());
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
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").value(userRequest.getEmail()))
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
                                .andDo(print())
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
                                .andDo(print())
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", createdUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
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
                duplicateUser.setEmail(userRequest.getEmail());
                duplicateUser.setPassword("password123");
                duplicateUser.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(duplicateUser)))
                                .andDo(print())
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
                duplicateUser.setEmail("otro@email.com");
                duplicateUser.setPassword("password123");
                duplicateUser.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(duplicateUser)))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenDeleteLastAdmin_thenBadRequest() throws Exception {

                mockMvc.perform(delete(BASE_URL + "/{id}", testAdmin.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenGetUsersByRole_thenSuccess() throws Exception {

                UserRequestDTO user1 = new UserRequestDTO();
                user1.setName("Chef Usuario");
                user1.setEmail("chef@test.com");
                user1.setPassword("password123");
                user1.setRole(Role.CHEF);

                UserRequestDTO user2 = new UserRequestDTO();
                user2.setName("User Usuario");
                user2.setEmail("user@test.com");
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
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].role").value("CHEF"));

                mockMvc.perform(get(BASE_URL + "/by-role/ADMIN")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].role").value("ADMIN"));
        }

        @Test
        void whenCreateUserWithInvalidEmail_thenBadRequest() throws Exception {
                UserRequestDTO userRequest = new UserRequestDTO();
                userRequest.setName("Usuario Test");
                userRequest.setEmail("email-invalido");
                userRequest.setPassword("password123");
                userRequest.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenCreateUserWithShortPassword_thenBadRequest() throws Exception {
                UserRequestDTO userRequest = new UserRequestDTO();
                userRequest.setName("Usuario Test");
                userRequest.setEmail("test@valid.com");
                userRequest.setPassword("12345");
                userRequest.setRole(Role.USER);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(userRequest)))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenUpdateUserEmail_WithExistingEmail_thenBadRequest() throws Exception {

                UserRequestDTO user1 = TestDataUtil.createUserRequestDTO();
                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(user1)))
                                .andExpect(status().isCreated());

                UserRequestDTO user2 = new UserRequestDTO();
                user2.setName("Segundo Usuario");
                user2.setEmail("segundo@test.com");
                user2.setPassword("password123");
                user2.setRole(Role.USER);

                String response2 = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(user2)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UserResponseDTO createdUser2 = objectMapper.readValue(response2, UserResponseDTO.class);

                user2.setEmail(user1.getEmail());

                mockMvc.perform(put(BASE_URL + "/{id}", createdUser2.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(user2)))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }
}
