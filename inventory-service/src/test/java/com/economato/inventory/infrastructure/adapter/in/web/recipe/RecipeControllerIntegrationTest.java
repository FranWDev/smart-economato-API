package com.economato.inventory.infrastructure.adapter.in.web.recipe;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.application.dto.recipe.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeRequestDTO;
import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class RecipeControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/recipes";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private StockLedgerRepository stockLedgerRepository;

        @Autowired
        private ProductBatchRepository productBatchRepository;

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

                ProductBatch batch = new ProductBatch();
                batch.setProduct(testProduct);
                batch.setInitialQuantity(testProduct.getCurrentStock());
                batch.setRemainingQuantity(testProduct.getCurrentStock());
                batch.setExpirationDate(LocalDate.now().plusYears(1));
                batch.setReceivedAt(LocalDateTime.now());
                batch.setDepleted(false);
                productBatchRepository.saveAndFlush(batch);

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
                recipeRequest.setPortions(new BigDecimal("4.0"));
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
                recipeRequest.setPortions(new BigDecimal("4.0"));
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
                recipeRequest.setPortions(new BigDecimal("4.0"));
                recipeRequest.setComponents(components);

                mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
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

                recipe1.setPortions(new BigDecimal("4.0"));
                recipe2.setPortions(new BigDecimal("4.0"));
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
                recipeRequest.setPortions(new BigDecimal("4.0"));
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
                recipeRequest.setPortions(new BigDecimal("4.0"));
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
                recipeRequest.setPortions(new BigDecimal("8.0"));
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
                assertTrue(ledgerEntries.stream().anyMatch(entry -> entry.getMovementType() == MovementType.SALIDA
                                && entry.getQuantityDelta().compareTo(new BigDecimal("-0.6")) == 0));
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
                recipeRequest.setPortions(new BigDecimal("1.0"));
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
                recipeRequest.setPortions(new BigDecimal("1.0"));
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
                recipeRequest.setPortions(new BigDecimal("10.0"));
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

        @Test
        void whenDownloadRecipePdf_WithValidRecipe_thenReturnsPdf() throws Exception {

                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Paella Valenciana");
                recipeRequest.setElaboration(
                                "1. Sofrir el pollo\n2. Añadir el arroz\n3. Agregar el caldo\n4. Cocinar 18 minutos");
                recipeRequest.setPresentation("Servir en paellera tradicional con limón");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setPortions(new BigDecimal("4.0"));
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                byte[] pdfBytes = mockMvc.perform(get(BASE_URL + "/{id}/pdf", recipeId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Type", "application/pdf"))
                                .andExpect(header().exists("Content-Disposition"))
                                .andReturn().getResponse().getContentAsByteArray();

                assertNotNull(pdfBytes);
                assertTrue(pdfBytes.length > 0);

                String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, 4));
                assertEquals("%PDF", pdfHeader);
        }

        @Test
        void whenDownloadRecipePdf_WithNonExistentRecipe_thenReturnsNotFound() throws Exception {
                mockMvc.perform(get(BASE_URL + "/{id}/pdf", 99999)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenDownloadRecipePdf_WithSpecialCharactersInName_thenSanitizesFilename() throws Exception {

                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Paella/Valenciana: ¡La Mejor! <2024>");
                recipeRequest.setElaboration("Elaboración básica");
                recipeRequest.setPresentation("Presentación tradicional");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setPortions(new BigDecimal("4.0"));
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/{id}/pdf", recipeId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Type", "application/pdf"))
                                .andExpect(header().string("Content-Disposition",
                                                matchesPattern("attachment; filename=\\\"Paella_Valenciana_+La_Mejor_+2024_\\.pdf\\\"")));
        }

        @Test
        void whenDownloadRecipePdf_WithLongName_thenTruncatesFilename() throws Exception {

                String longName = "Esta es una receta con un nombre extremadamente largo que debería ser truncado para evitar problemas con el sistema de archivos";
                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName(longName);
                recipeRequest.setElaboration("Elaboración");
                recipeRequest.setPresentation("Presentación");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setPortions(new BigDecimal("4.0"));
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                String contentDisposition = mockMvc.perform(get(BASE_URL + "/{id}/pdf", recipeId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Type", "application/pdf"))
                                .andExpect(header().exists("Content-Disposition"))
                                .andReturn().getResponse().getHeader("Content-Disposition");

                String filename = contentDisposition.substring(
                                contentDisposition.indexOf("filename=\"") + 10,
                                contentDisposition.lastIndexOf("\""));

                assertTrue(filename.length() <= 255,
                                "El nombre del archivo debería estar truncado a 255 caracteres o menos");
        }

        @Test
        void whenDownloadRecipePdf_WithUnicodeCharacters_thenHandlesCorrectly() throws Exception {

                RecipeRequestDTO recipeRequest = new RecipeRequestDTO();
                recipeRequest.setName("Paëlla Española 🥘 café");
                recipeRequest.setElaboration("Elaboración");
                recipeRequest.setPresentation("Presentación");

                List<RecipeComponentRequestDTO> components = new ArrayList<>();
                RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
                component.setProductId(testProduct.getId());
                component.setQuantity(new BigDecimal("0.5"));
                components.add(component);
                recipeRequest.setPortions(new BigDecimal("4.0"));
                recipeRequest.setComponents(components);

                String createResponse = mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(asJsonString(recipeRequest))
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/{id}/pdf", recipeId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Type", "application/pdf"))
                                .andExpect(header().exists("Content-Disposition"));
        }

}
