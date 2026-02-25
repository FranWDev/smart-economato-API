package com.economato.inventory.controller;

import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.RecipeRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatsControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        recipeRepository.deleteAll();
        userRepository.deleteAll();

        User admin = TestDataUtil.createAdminUser();
        userRepository.saveAndFlush(admin);
    }

    @Test
    void getRecipeStats_WhenAdmin_ShouldReturnStats() throws Exception {
        String token = loginAsAdmin();

        // Create some recipes
        recipeRepository
                .save(TestDataUtil.createRecipe("Recipe 1", "Elaboration", "Presentation", new BigDecimal("10.00")));
        recipeRepository
                .save(TestDataUtil.createRecipe("Recipe 2", "Elaboration", "Presentation", new BigDecimal("20.00")));

        ResultActions response = mockMvc.perform(get("/api/stats/recipes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecipes", is(2)))
                .andExpect(jsonPath("$.averagePrice", is(15.0)));
    }

    @Test
    void getUserStats_WhenAdmin_ShouldReturnStats() throws Exception {
        String token = loginAsAdmin();

        // Admin already created in setUp (adminUser / admin123)
        userRepository.save(TestDataUtil.createUser("user1", "u1", "Pass123", Role.USER));
        userRepository.save(TestDataUtil.createUser("user2", "u2", "Pass123", Role.USER));
        userRepository.save(TestDataUtil.createUser("chef1", "c1", "Pass123", Role.CHEF));

        ResultActions response = mockMvc.perform(get("/api/stats/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", is(4))) // 1 admin + 2 users + 1 chef
                .andExpect(jsonPath("$.usersByRole.ADMIN", is(1)))
                .andExpect(jsonPath("$.usersByRole.USER", is(2)))
                .andExpect(jsonPath("$.usersByRole.CHEF", is(1)));
    }

    @Test
    void getStats_WhenNotAdmin_ShouldReturnForbidden() throws Exception {
        userRepository.save(TestDataUtil.createUser("Regular", "regular", "user123", Role.USER));
        String token = login("Regular", "user123");

        mockMvc.perform(get("/api/stats/recipes")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/stats/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
