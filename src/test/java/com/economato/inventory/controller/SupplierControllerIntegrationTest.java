package com.economato.inventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.SupplierRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

class SupplierControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/suppliers";
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
        void whenGetAllSuppliers_thenReturnsPaginatedSuppliers() throws Exception {
                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", notNullValue()));
        }

        @Test
        void whenCreateValidSupplier_thenReturnsCreatedSupplier() throws Exception {
                SupplierRequestDTO supplier = new SupplierRequestDTO();
                supplier.setName("Distribuidora Nacional S.A.");
                supplier.setEmail("distri@nacional.com");
                supplier.setPhone("123456789");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name", is(supplier.getName())))
                                .andExpect(jsonPath("$.email", is(supplier.getEmail())))
                                .andExpect(jsonPath("$.phone", is(supplier.getPhone())));
        }

        @Test
        void whenCreateSupplierWithDuplicateName_thenReturnsBadRequest() throws Exception {
                SupplierRequestDTO supplier = new SupplierRequestDTO();
                supplier.setName("Proveedor Duplicado");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier)))
                                .andExpect(status().isCreated());

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier)))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenGetSupplierById_thenReturnsSupplier() throws Exception {
                SupplierRequestDTO supplier = new SupplierRequestDTO();
                supplier.setName("Proveedor Test");

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer supplierId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/{id}", supplierId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(supplier.getName())));
        }

        @Test
        void whenUpdateSupplier_thenReturnsUpdatedSupplier() throws Exception {
                SupplierRequestDTO supplier = new SupplierRequestDTO();
                supplier.setName("Proveedor Original");

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer supplierId = objectMapper.readTree(response).get("id").asInt();

                supplier.setName("Proveedor Actualizado");
                supplier.setEmail("actualizado@proveedor.com");
                supplier.setPhone("987654321");

                mockMvc.perform(put(BASE_URL + "/{id}", supplierId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(supplier.getName())))
                                .andExpect(jsonPath("$.email", is(supplier.getEmail())))
                                .andExpect(jsonPath("$.phone", is(supplier.getPhone())));
        }

        @Test
        void whenDeleteSupplier_thenReturnsNoContent() throws Exception {
                SupplierRequestDTO supplier = new SupplierRequestDTO();
                supplier.setName("Proveedor a Eliminar");

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer supplierId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(delete(BASE_URL + "/{id}", supplierId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", supplierId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenSearchSuppliersByName_thenReturnsMatchingSuppliers() throws Exception {
                SupplierRequestDTO supplier1 = new SupplierRequestDTO();
                supplier1.setName("Distribuidora ABC");

                SupplierRequestDTO supplier2 = new SupplierRequestDTO();
                supplier2.setName("Distribuidora XYZ");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier1)))
                                .andExpect(status().isCreated());

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier2)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/search")
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("name", "Distribuidora"))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void whenGetNonExistentSupplier_thenReturnsNotFound() throws Exception {
                mockMvc.perform(get(BASE_URL + "/{id}", 99999)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenUpdateNonExistentSupplier_thenReturnsNotFound() throws Exception {
                SupplierRequestDTO supplier = new SupplierRequestDTO();
                supplier.setName("Proveedor Inexistente");

                mockMvc.perform(put(BASE_URL + "/{id}", 99999)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(supplier)))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }
}
