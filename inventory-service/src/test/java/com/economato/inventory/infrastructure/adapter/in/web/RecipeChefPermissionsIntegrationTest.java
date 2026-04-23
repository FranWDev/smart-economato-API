package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.*;
import com.economato.inventory.application.dto.response.LoginResponseDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeChefPermissionsIntegrationTest extends BaseIntegrationTest {

    private static final String RECIPES_URL = "/api/recipes";
    private static final String DRAFTS_URL = "/api/recipe-drafts";
    private static final String AUTH_URL = "/api/auth/login";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    private String chefToken;
    private Product testProduct;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();

        User chefUser = TestDataUtil.createChefUser();
        chefUser = userRepository.saveAndFlush(chefUser);

        testProduct = TestDataUtil.createFlour();
        testProduct = productRepository.saveAndFlush(testProduct);

        chefToken = login("chefUser", "chef123");
    }

    @Test
    void chefCanCreateEditAndDeleteRecipe() throws Exception {
        // 1. Create
        RecipeRequestDTO request = new RecipeRequestDTO();
        request.setName("Chef Special");
        request.setElaboration("Mix and cook");
        request.setPresentation("Nice plate");
        request.setPortions(new BigDecimal("1.0"));
        
        RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
        component.setProductId(testProduct.getId());
        component.setQuantity(new BigDecimal("0.5"));
        request.setComponents(List.of(component));

        String createResponse = mockMvc.perform(post(RECIPES_URL)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer recipeId = objectMapper.readTree(createResponse).get("id").asInt();

        // 2. Edit
        request.setName("Chef Special Updated");
        mockMvc.perform(put(RECIPES_URL + "/{id}", recipeId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Chef Special Updated")));

        // 3. Delete
        mockMvc.perform(delete(RECIPES_URL + "/{id}", recipeId)
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isNoContent());

        // 4. Verify deleted
        mockMvc.perform(get(RECIPES_URL + "/{id}", recipeId)
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void chefCanApproveAndRejectDrafts() throws Exception {
        // 1. Create a draft (using chef token is fine as they have USER perms too)
        RecipeDraftRequestDTO draftRequest = new RecipeDraftRequestDTO();
        draftRequest.setName("Draft for Chef");
        draftRequest.setElaboration("TBD");
        draftRequest.setPresentation("TBD");
        draftRequest.setPortions(new BigDecimal("1.0"));
        
        RecipeComponentRequestDTO draftComponent = new RecipeComponentRequestDTO();
        draftComponent.setProductId(testProduct.getId());
        draftComponent.setQuantity(new BigDecimal("0.5"));
        draftRequest.setComponents(List.of(draftComponent));
        
        draftRequest.setAllergenIds(List.of());

        String draftResponse = mockMvc.perform(post(DRAFTS_URL)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(draftRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer draftId = objectMapper.readTree(draftResponse).get("id").asInt();

        // 2. Reject it first
        RecipeDraftRejectRequestDTO rejectDTO = new RecipeDraftRejectRequestDTO();
        rejectDTO.setReason("Need more info");

        mockMvc.perform(patch(DRAFTS_URL + "/{id}/reject", draftId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(rejectDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));

        // 3. Approve it (need to update it first to make it PENDING again if system requires, 
        // but let's see if we can just approve it for simplicity)
        // Actually, update it to make it PENDING
        mockMvc.perform(put(DRAFTS_URL + "/{id}", draftId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(draftRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")));

        mockMvc.perform(patch(DRAFTS_URL + "/{id}/approve", draftId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }
}
