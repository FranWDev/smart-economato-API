package com.economatom.inventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.economatom.inventory.dto.request.AllergenRequestDTO;
import com.economatom.inventory.dto.request.LoginRequestDTO;
import com.economatom.inventory.dto.response.LoginResponseDTO;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.UserRepository;
import com.economatom.inventory.util.TestDataUtil;

class AllergenControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/allergens";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private UserRepository userRepository;

        private String jwtToken;
        private User testUser;

        @BeforeEach
        void setUp() throws Exception {

                entityManager.clear();
                clearDatabase();

                testUser = TestDataUtil.createAdminUser();

                userRepository.saveAndFlush(testUser);

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(testUser.getName());
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
        void whenGetAllAllergens_thenReturnsPaginatedAllergens() throws Exception {
                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", notNullValue()));
        }

        @Test
        void whenCreateValidAllergen_thenReturnsCreatedAllergen() throws Exception {

                AllergenRequestDTO allergen = new AllergenRequestDTO();
                allergen.setName(TestDataUtil.createNutsAllergen().getName());

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(allergen)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name", is(allergen.getName())));
        }

        @Test
        void whenGetAllergenById_thenReturnsAllergen() throws Exception {

                AllergenRequestDTO allergen = new AllergenRequestDTO();
                allergen.setName(TestDataUtil.createEggAllergen().getName());

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(allergen)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer allergenId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/{id}", allergenId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(allergen.getName())));
        }

        @Test
        void whenUpdateAllergen_thenReturnsUpdatedAllergen() throws Exception {

                AllergenRequestDTO allergen = new AllergenRequestDTO();
                allergen.setName(TestDataUtil.createGlutenAllergen().getName());

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(allergen)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer allergenId = objectMapper.readTree(response).get("id").asInt();

                allergen.setName(allergen.getName() + " Actualizado");

                mockMvc.perform(put(BASE_URL + "/{id}", allergenId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(allergen)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(allergen.getName())));
        }

        @Test
        void whenDeleteAllergen_thenReturnsNoContent() throws Exception {

                AllergenRequestDTO allergen = new AllergenRequestDTO();
                allergen.setName(TestDataUtil.createNutsAllergen().getName());

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(allergen)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer allergenId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(delete(BASE_URL + "/{id}", allergenId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", allergenId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenSearchAllergenByName_thenReturnsAllergen() throws Exception {
                AllergenRequestDTO allergen = new AllergenRequestDTO();
                allergen.setName(TestDataUtil.createGlutenAllergen().getName());

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(allergen)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/search")
                                .param("name", allergen.getName())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(allergen.getName())));
        }
}