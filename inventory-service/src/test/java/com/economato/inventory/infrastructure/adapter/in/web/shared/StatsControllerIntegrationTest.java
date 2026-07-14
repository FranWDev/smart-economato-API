package com.economato.inventory.infrastructure.adapter.in.web.shared;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Allergen;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.AllergenRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

class StatsControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RecipeRepository recipeRepository;

        @Autowired
        private AllergenRepository allergenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        clearDatabase();

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
    void getRecipesWithAllergensCount_WhenAdmin_ShouldReturnCount() throws Exception {
        String token = loginAsAdmin();

        Allergen gluten = allergenRepository.save(TestDataUtil.createGlutenAllergen());
        Recipe recipeWithAllergen = TestDataUtil.createRecipe("Recipe With Allergen", "Elaboration", "Presentation",
                new BigDecimal("12.00"));
        recipeWithAllergen.getAllergens().add(gluten);

        Recipe recipeWithoutAllergen = TestDataUtil.createRecipe("Recipe Without Allergen", "Elaboration",
                "Presentation", new BigDecimal("8.00"));

        recipeRepository.save(recipeWithAllergen);
        recipeRepository.save(recipeWithoutAllergen);

        mockMvc.perform(get("/api/stats/recipes/with-allergens/count")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)));
    }

    @Test
    void getRecipesWithoutAllergensCount_WhenAdmin_ShouldReturnCount() throws Exception {
        String token = loginAsAdmin();

        recipeRepository
                .save(TestDataUtil.createRecipe("Recipe 1", "Elaboration", "Presentation", new BigDecimal("10.00")));
        recipeRepository
                .save(TestDataUtil.createRecipe("Recipe 2", "Elaboration", "Presentation", new BigDecimal("20.00")));

        mockMvc.perform(get("/api/stats/recipes/without-allergens/count")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(2)));
    }

    @Test
    void getRecipesAverageCost_WhenAdmin_ShouldReturnAverageCost() throws Exception {
        String token = loginAsAdmin();

        recipeRepository
                .save(TestDataUtil.createRecipe("Recipe 1", "Elaboration", "Presentation", new BigDecimal("10.00")));
        recipeRepository
                .save(TestDataUtil.createRecipe("Recipe 2", "Elaboration", "Presentation", new BigDecimal("20.00")));

        mockMvc.perform(get("/api/stats/recipes/average-cost")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageCost", is(15.0)));
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
    void getProductStats_WhenAdmin_ShouldReturnStats() throws Exception {
        String token = loginAsAdmin();

        // Create some products
        // total inventory value = (10.00 * 5.0) + (20.00 * 2.0) = 50 + 40 = 90
        // average price = (10 + 20) / 2 = 15
        Product p1 = TestDataUtil.createProduct("Product 1", "KG", new BigDecimal("10.00"), "P1", new BigDecimal("5.0"));
        Product p2 = TestDataUtil.createProduct("Product 2", "KG", new BigDecimal("20.00"), "P2", new BigDecimal("2.0"));
        
        productRepository.saveAllAndFlush(Arrays.asList(p1, p2));

        mockMvc.perform(get("/api/stats/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts", is(2)))
                .andExpect(jsonPath("$.totalInventoryValue", is(90.0)))
                .andExpect(jsonPath("$.averagePrice", is(15.0)));
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

        mockMvc.perform(get("/api/stats/recipes/with-allergens/count")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/stats/recipes/without-allergens/count")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/stats/recipes/average-cost")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
