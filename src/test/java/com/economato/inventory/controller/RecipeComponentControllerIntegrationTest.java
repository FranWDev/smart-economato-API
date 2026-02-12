package com.economato.inventory.controller;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.dto.response.RecipeComponentResponseDTO;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

public class RecipeComponentControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/recipe-components";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private RecipeRepository recipeRepository;

        @Autowired
        private UserRepository userRepository;

        private String jwtToken;
        private Product testProduct;
        private Recipe testRecipe;
        private User testUser;

        @Transactional
        @BeforeEach
        void setUp() throws Exception {
                clearDatabase();

                testUser = TestDataUtil.createAdminUser();
                testUser = userRepository.saveAndFlush(testUser);

                testProduct = new Product();
                testProduct.setName("Harina");
                testProduct.setType("Ingrediente");
                testProduct.setUnit("KG");
                testProduct.setUnitPrice(new BigDecimal("2.50"));
                testProduct.setProductCode("HAR001");
                testProduct.setCurrentStock(new BigDecimal("100.0"));
                testProduct.setMinimumStock(BigDecimal.ZERO); // Required field
                testProduct = productRepository.save(testProduct);

                testRecipe = new Recipe();
                testRecipe.setName("Pastel básico");
                testRecipe.setElaboration("blabalbal");
                testRecipe.setPresentation("blablabla");
                testRecipe.setTotalCost(new BigDecimal("10.00"));
                testRecipe = recipeRepository.saveAndFlush(testRecipe);

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
        void whenCreateRecipeComponent_thenSuccess() throws Exception {
                RecipeComponentRequestDTO componentRequest = new RecipeComponentRequestDTO();
                componentRequest.setProductId(testProduct.getId());
                componentRequest.setRecipeId(testRecipe.getId());
                componentRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL + "/recipe/" + testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(componentRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.parentRecipeId").value(testRecipe.getId()))
                                .andExpect(jsonPath("$.productId").value(testProduct.getId()))
                                .andExpect(jsonPath("$.quantity").value(2.5));
        }

        @Test
        void whenGetAllRecipeComponents_thenSuccess() throws Exception {
                RecipeComponentRequestDTO componentRequest = new RecipeComponentRequestDTO();
                componentRequest.setProductId(testProduct.getId());
                componentRequest.setRecipeId(testRecipe.getId());
                componentRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL + "/recipe/" + testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(componentRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/recipe/" + testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].productId").value(testProduct.getId()))
                                .andExpect(jsonPath("$[0].quantity").value(2.5));
        }

        @Test
        void whenGetRecipeComponentById_thenSuccess() throws Exception {
                RecipeComponentRequestDTO componentRequest = new RecipeComponentRequestDTO();
                componentRequest.setProductId(testProduct.getId());
                componentRequest.setRecipeId(testRecipe.getId());
                componentRequest.setQuantity(new BigDecimal("2.5"));

                String createdComponentResponse = mockMvc.perform(post(BASE_URL + "/recipe/" + testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(componentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                RecipeComponentResponseDTO createdComponent = objectMapper.readValue(createdComponentResponse,
                                RecipeComponentResponseDTO.class);

                mockMvc.perform(get(BASE_URL + "/{id}", createdComponent.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productId").value(testProduct.getId()))
                                .andExpect(jsonPath("$.quantity").value(2.5));
        }

        @Test
        void whenGetComponentsByRecipeId_thenSuccess() throws Exception {
                RecipeComponentRequestDTO componentRequest = new RecipeComponentRequestDTO();
                componentRequest.setProductId(testProduct.getId());
                componentRequest.setRecipeId(testRecipe.getId());
                componentRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL + "/recipe/" + testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(componentRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/recipe/{recipeId}", testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].productId").value(testProduct.getId()))
                                .andExpect(jsonPath("$[0].quantity").value(2.5));
        }

        @Test
        void whenGetAllComponents_thenSuccess() throws Exception {
                RecipeComponentRequestDTO componentRequest = new RecipeComponentRequestDTO();
                componentRequest.setProductId(testProduct.getId());
                componentRequest.setRecipeId(testRecipe.getId());
                componentRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL + "/recipe/" + testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(componentRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL)
                                .param("page", "0")
                                .param("size", "10")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }

        @Test
        void whenUpdateComponent_thenSuccess() throws Exception {
                RecipeComponentRequestDTO componentRequest = new RecipeComponentRequestDTO();
                componentRequest.setProductId(testProduct.getId());
                componentRequest.setRecipeId(testRecipe.getId());
                componentRequest.setQuantity(new BigDecimal("2.5"));

                String createdComponentResponse = mockMvc.perform(post(BASE_URL + "/recipe/" + testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(componentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                RecipeComponentResponseDTO createdComponent = objectMapper.readValue(createdComponentResponse,
                                RecipeComponentResponseDTO.class);

                componentRequest.setQuantity(new BigDecimal("5.0"));

                mockMvc.perform(put(BASE_URL + "/{id}", createdComponent.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(componentRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.quantity").value(5.0));
        }

        @Test
        void whenDeleteComponent_thenSuccess() throws Exception {
                RecipeComponentRequestDTO componentRequest = new RecipeComponentRequestDTO();
                componentRequest.setProductId(testProduct.getId());
                componentRequest.setRecipeId(testRecipe.getId());
                componentRequest.setQuantity(new BigDecimal("2.5"));

                String createdComponentResponse = mockMvc.perform(post(BASE_URL + "/recipe/" + testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(componentRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                RecipeComponentResponseDTO createdComponent = objectMapper.readValue(createdComponentResponse,
                                RecipeComponentResponseDTO.class);

                mockMvc.perform(delete(BASE_URL + "/{id}", createdComponent.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", createdComponent.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }
}
