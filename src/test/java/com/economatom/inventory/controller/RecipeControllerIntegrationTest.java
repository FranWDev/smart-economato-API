package com.economatom.inventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.economatom.inventory.dto.request.LoginRequestDTO;
import com.economatom.inventory.dto.response.LoginResponseDTO;
import com.economatom.inventory.dto.request.RecipeRequestDTO;
import com.economatom.inventory.dto.request.RecipeComponentRequestDTO;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.ProductRepository;
import com.economatom.inventory.repository.UserRepository;
import com.economatom.inventory.util.TestDataUtil;

class RecipeControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/recipes";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private UserRepository userRepository;

        private Product testProduct;
        private User testUser;
        private String jwtToken;

        @BeforeEach
        void setUp() throws Exception {
                clearDatabase();

                testUser = TestDataUtil.createAdminUser();
                testUser = userRepository.saveAndFlush(testUser);

                testProduct = TestDataUtil.createFlour();
                testProduct = productRepository.saveAndFlush(testProduct);

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
        void whenCreateValidRecipe_thenReturnsCreatedRecipe() throws Exception {
                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Paella Valenciana");
                recipeRequest.setElaboration("1. Sofrito\n2. Arroz\n3. Caldo");
                recipeRequest.setPresentation("Presentación tradicional");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setComponents(components);

                mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name", is(recipeRequest.getName())))
                                .andExpect(jsonPath("$.components", hasSize(1)))
                                .andExpect(jsonPath("$.components[0].productId", is(testProduct.getId())))
                                .andExpect(jsonPath("$.components[0].quantity", is(0.5)));
        }

        @Test
        void whenGetRecipeById_thenReturnsRecipe() throws Exception {
                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Paella Valenciana");
                recipeRequest.setElaboration("1. Sofrito\n2. Arroz\n3. Caldo");
                recipeRequest.setPresentation("Presentación tradicional");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/{id}", recipeId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(recipeRequest.getName())))
                                .andExpect(jsonPath("$.components", hasSize(1)))
                                .andExpect(jsonPath("$.components[0].productId", is(testProduct.getId())))
                                .andExpect(jsonPath("$.components[0].quantity", is(0.5)));
        }

        @Test
        void whenGetAllRecipes_thenReturnsList() throws Exception {
                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Paella Valenciana");
                recipeRequest.setElaboration("1. Sofrito\n2. Arroz\n3. Caldo");
                recipeRequest.setPresentation("Presentación tradicional");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setComponents(components);

                mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        void whenSearchRecipesByName_thenReturnsMatchingRecipes() throws Exception {
                RecipeRequestDTO recipe1 = new RecipeRequestDTO();
                recipe1.setName("Paella Valenciana");
                recipe1.setElaboration("1. Sofrito\n2. Arroz\n3. Caldo");
                recipe1.setPresentation("Presentación tradicional");

                RecipeRequestDTO recipe2 = new RecipeRequestDTO();
                recipe2.setName("Paella de Marisco");
                recipe2.setElaboration("1. Sofrito de pescado\n2. Arroz\n3. Fumet");
                recipe2.setPresentation("Presentación con mariscos");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);

                recipe1.setComponents(new ArrayList<>(components));
                recipe2.setComponents(new ArrayList<>(components));

                mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipe1))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated());

                mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipe2))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/search")
                                .param("name", "Paella")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)))
                                .andExpect(jsonPath("$[*].name", everyItem(containsString("Paella"))));
        }

        @Test
        void whenUpdateRecipe_thenReturnsUpdatedRecipe() throws Exception {
                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Paella Original");
                recipeRequest.setElaboration("Elaboración original");
                recipeRequest.setPresentation("Presentación original");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                recipeRequest.setName("Paella Actualizada");
                mockMvc.perform(put(BASE_URL + "/{id}", recipeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Paella Actualizada")));
        }

        @Test
        void whenDeleteRecipe_thenReturnsNoContent() throws Exception {
                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Paella a Eliminar");
                recipeRequest.setElaboration("Elaboración");
                recipeRequest.setPresentation("Presentación");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                mockMvc.perform(delete(BASE_URL + "/{id}", recipeId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", recipeId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenSearchByMaxCost_thenReturnsMatchingRecipes() throws Exception {
                mockMvc.perform(get(BASE_URL + "/maxcost")
                                .param("maxCost", "100.00")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }
}
