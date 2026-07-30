package com.economato.inventory.infrastructure.adapter.in.web.recipe;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;

import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeDraftRejectRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeDraftRequestDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeDraftControllerIntegrationTest extends BaseIntegrationTest {

    private static final String AUTH_URL = "/api/auth/login";
    private static final String BASE_URL = "/api/recipe-drafts";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    private String userToken;
    private String adminToken;
    private Product testProduct;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();

        User adminUser = userRepository.saveAndFlush(TestDataUtil.createAdminUser());
        User regularUser = userRepository.saveAndFlush(TestDataUtil.createRegularUser());

        testProduct = productRepository.saveAndFlush(TestDataUtil.createFlour());

        adminToken = loginByNameAndPassword(adminUser.getName(), "admin123");
        userToken = loginByNameAndPassword(regularUser.getName(), "user123");
    }

    @Test
    void whenUserCreatesDraft_thenCreatedAndMineContainsIt() throws Exception {
        RecipeDraftRequestDTO request = buildDraftRequest("Borrador tortilla");

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Borrador tortilla")))
                .andExpect(jsonPath("$.status", is("PENDING")));

        mockMvc.perform(get(BASE_URL + "/mine")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void whenAdminApprovesDraft_thenStatusApprovedAndRecipeIdIsPresent() throws Exception {
        RecipeDraftRequestDTO request = buildDraftRequest("Borrador paella");

        String createResponse = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer draftId = objectMapper.readTree(createResponse).get("id").asInt();

        mockMvc.perform(patch(BASE_URL + "/{id}/approve", draftId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.approvedRecipeId").isNumber());
    }

    @Test
    void whenUserTriesToListAllDrafts_thenForbidden() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAdminRejectsAndUserUpdatesDraft_thenDraftReturnsToPending() throws Exception {
        RecipeDraftRequestDTO request = buildDraftRequest("Borrador croqueta");

        String createResponse = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer draftId = objectMapper.readTree(createResponse).get("id").asInt();

        RecipeDraftRejectRequestDTO rejectDTO = new RecipeDraftRejectRequestDTO();
        rejectDTO.setReason("Falta detalle de elaboración");

        mockMvc.perform(patch(BASE_URL + "/{id}/reject", draftId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(rejectDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));

        request.setElaboration("Paso 1 actualizado");
        mockMvc.perform(put(BASE_URL + "/{id}", draftId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    private RecipeDraftRequestDTO buildDraftRequest(String name) {
        RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
        component.setProductId(testProduct.getId());
        component.setQuantity(new BigDecimal("0.5"));

        RecipeDraftRequestDTO request = new RecipeDraftRequestDTO();
        request.setName(name);
        request.setElaboration("Paso 1");
        request.setPresentation("Presentación test");
        request.setPortions(new BigDecimal("2.0"));
        request.setComponents(List.of(component));
        request.setAllergenIds(List.of());
        request.setHidden(false);
        return request;
    }

    private String loginByNameAndPassword(String name, String password) throws Exception {
        return login(name, password);
    }
}
