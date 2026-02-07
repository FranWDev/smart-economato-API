package com.economato.inventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.dto.request.RecipeRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.StockLedgerRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

class RecipeControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/recipes";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private StockLedgerRepository stockLedgerRepository;

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

        @Test
        void whenCookRecipe_WithValidData_thenSuccessfullyDeductsStock() throws Exception {

                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Pizza Margherita");
                recipeRequest.setElaboration("1. Masa\n2. Salsa\n3. Queso");
                recipeRequest.setPresentation("En bandeja");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.3"));
                components.add(component);
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
                cookingRequest.setRecipeId(recipeId);
                cookingRequest.setQuantity(new BigDecimal("2.0"));
                cookingRequest.setDetails("Pedido mesa 5");

                mockMvc.perform(post(BASE_URL + "/cook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(cookingRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(recipeRequest.getName())))
                                .andExpect(jsonPath("$.id", is(recipeId)));

                List<StockLedger> ledgerEntries = stockLedgerRepository
                                .findByProductIdOrderBySequenceNumber(testProduct.getId());
                assertTrue(ledgerEntries.stream().anyMatch(entry -> entry.getMovementType() == MovementType.SALIDA &&
                                entry.getQuantityDelta().compareTo(new BigDecimal("-0.6")) == 0));
        }

        @Test
        void whenCookRecipe_WithInsufficientStock_thenReturnsBadRequest() throws Exception {

                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Mega Pizza");
                recipeRequest.setElaboration("Elaboración");
                recipeRequest.setPresentation("Presentación");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("50.0"));
                components.add(component);
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
                cookingRequest.setRecipeId(recipeId);
                cookingRequest.setQuantity(new BigDecimal("100.0"));

                mockMvc.perform(post(BASE_URL + "/cook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(cookingRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("Stock insuficiente")));
        }

        @Test
        void whenCookRecipe_WithNonExistentRecipe_thenReturnsNotFound() throws Exception {
                RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
                cookingRequest.setRecipeId(9999);
                cookingRequest.setQuantity(new BigDecimal("1.0"));

                mockMvc.perform(post(BASE_URL + "/cook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(cookingRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenCookRecipe_WithInvalidQuantity_thenReturnsBadRequest() throws Exception {
                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Simple Recipe");
                recipeRequest.setElaboration("Elaboración");
                recipeRequest.setPresentation("Presentación");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.1"));
                components.add(component);
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                String invalidRequest = String.format(
                                "{\"recipeId\": %d, \"quantity\": -1.0}",
                                recipeId);

                mockMvc.perform(post(BASE_URL + "/cook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenCookRecipe_WithFractionalQuantity_thenSuccessfullyDeductsCorrectAmount() throws Exception {

                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Tarta");
                recipeRequest.setElaboration("Elaboración");
                recipeRequest.setPresentation("Presentación");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("2.5"));
                components.add(component);
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                BigDecimal initialStock = testProduct.getCurrentStock();

                RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
                cookingRequest.setRecipeId(recipeId);
                cookingRequest.setQuantity(new BigDecimal("1.5"));
                cookingRequest.setDetails("Media tarta extra");

                mockMvc.perform(post(BASE_URL + "/cook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(cookingRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk());

                Product updatedProduct = productRepository.findById(testProduct.getId()).orElse(null);
                assertNotNull(updatedProduct);
                BigDecimal expectedStock = initialStock.subtract(new BigDecimal("3.75"));
                assertEquals(0, expectedStock.compareTo(updatedProduct.getCurrentStock()));
        }
}
