package com.economato.inventory.infrastructure.adapter.in.web.recipe;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;

import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.domain.model.recipe.Allergen;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.AllergenRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.shared.TestDataUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Arrays;
import java.util.HashSet;

import static org.hamcrest.Matchers.*;

public class RecipeAllergenControllerIntegrationTest extends BaseIntegrationTest {
        private static final String BASE_URL = "/api/recipe-allergens";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private RecipeRepository recipeRepository;

        @Autowired
        private AllergenRepository allergenRepository;

        @Autowired
        private UserRepository userRepository;

        private String jwtToken;
        private Recipe testRecipe;
        private Allergen testAllergen;

        @BeforeEach
        public void setUp() throws Exception {

                clearDatabase();

                User adminUser = TestDataUtil.createAdminUser();
                userRepository.saveAndFlush(adminUser);

                jwtToken = loginAsAdmin();

                testAllergen = TestDataUtil.createGlutenAllergen();
                Allergen anotherAllergen = TestDataUtil.createEggAllergen();

                testAllergen = allergenRepository.saveAndFlush(testAllergen);
                anotherAllergen = allergenRepository.saveAndFlush(anotherAllergen);

                testRecipe = TestDataUtil.createBasicCakeRecipe();
                testRecipe.setAllergens(new HashSet<>(Arrays.asList(testAllergen, anotherAllergen)));
                testRecipe = recipeRepository.saveAndFlush(testRecipe);
        }

        @Test
        public void whenAddAllergenToRecipe_thenSuccess() throws Exception {
                mockMvc.perform(post(BASE_URL + "/{recipeId}/{allergenId}",
                                testRecipe.getId(), testAllergen.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk());
        }

        @Test
        public void whenAddAllergenToNonexistentRecipe_thenNotFound() throws Exception {
                mockMvc.perform(post(BASE_URL + "/{recipeId}/{allergenId}",
                                999, testAllergen.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        public void whenAddNonexistentAllergenToRecipe_thenNotFound() throws Exception {
                mockMvc.perform(post(BASE_URL + "/{recipeId}/{allergenId}",
                                testRecipe.getId(), 999)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        public void whenRemoveAllergenFromRecipe_thenSuccess() throws Exception {

                mockMvc.perform(post(BASE_URL + "/{recipeId}/{allergenId}",
                                testRecipe.getId(), testAllergen.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk());

                mockMvc.perform(delete(BASE_URL + "/recipe/{recipeId}/allergen/{allergenId}",
                                testRecipe.getId(), testAllergen.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());
        }

        @Test
        public void whenRemoveAllergenFromNonexistentRecipe_thenNotFound() throws Exception {
                mockMvc.perform(delete(BASE_URL + "/recipe/{recipeId}/allergen/{allergenId}",
                                999, testAllergen.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        public void whenRemoveNonexistentAllergenFromRecipe_thenNotFound() throws Exception {
                mockMvc.perform(delete(BASE_URL + "/recipe/{recipeId}/allergen/{allergenId}",
                                testRecipe.getId(), 999)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        public void whenGetAllergensForRecipe_thenSuccess() throws Exception {

                mockMvc.perform(post(BASE_URL + "/{recipeId}/{allergenId}",
                                testRecipe.getId(), testAllergen.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk());

                mockMvc.perform(get(BASE_URL + "/recipe/{recipeId}/allergens", testRecipe.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$").exists())
                                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }

        @Test
        public void whenGetAllergensForNonexistentRecipe_thenNotFound() throws Exception {
                mockMvc.perform(get(BASE_URL + "/recipe/{recipeId}/allergens", 999)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

}
