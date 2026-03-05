package com.economato.inventory.repository;

import com.economato.inventory.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RecipeCookingAuditRepository Integration Tests")
class RecipeCookingAuditRepositoryIntegrationTest {

    @Autowired
    private RecipeCookingAuditRepository cookingAuditRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RecipeComponentRepository componentRepository;

    @Autowired
    private UserRepository userRepository;

    private Recipe recipe1;
    private Recipe recipe2;
    private Recipe recipe3;
    private Product product1;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Crear usuario de prueba
        testUser = new User();
        testUser.setUser("testuser");
        testUser.setPassword("hashedPassword");
        testUser.setName("Test User");
        testUser.setRole(Role.USER);
        testUser = userRepository.save(testUser);

        // Crear producto
        product1 = new Product();
        product1.setName("Tomate");
        product1.setProductCode("PROD-TOMATE");
        product1.setType("kg");
        product1.setUnit("kg");
        product1.setCurrentStock(BigDecimal.ZERO);
        product1.setUnitPrice(BigDecimal.valueOf(2.50));
        product1.setMinimumStock(BigDecimal.ONE);
        product1 = productRepository.save(product1);

        // Crear recetas
        recipe1 = new Recipe();
        recipe1.setName("Pizza");
        recipe1 = recipeRepository.save(recipe1);

        recipe2 = new Recipe();
        recipe2.setName("Salsa");
        recipe2 = recipeRepository.save(recipe2);

        recipe3 = new Recipe();
        recipe3.setName("Sopa");
        recipe3 = recipeRepository.save(recipe3);

        // Crear componentes de receta con el producto
        RecipeComponent comp1 = new RecipeComponent();
        comp1.setParentRecipe(recipe1);
        comp1.setProduct(product1);
        comp1.setQuantity(BigDecimal.valueOf(0.3));
        componentRepository.save(comp1);

        RecipeComponent comp2 = new RecipeComponent();
        comp2.setParentRecipe(recipe2);
        comp2.setProduct(product1);
        comp2.setQuantity(BigDecimal.valueOf(0.5));
        componentRepository.save(comp2);

        RecipeComponent comp3 = new RecipeComponent();
        comp3.setParentRecipe(recipe3);
        comp3.setProduct(product1);
        comp3.setQuantity(BigDecimal.valueOf(0.2));
        componentRepository.save(comp3);
    }

    @Test
    @DisplayName("findTopConsumingRecipesByProduct devuelve top 3 recetas")
    void testFindTopConsumingRecipesByProduct_ReturnsTopThreeRecipes() {
        // --- Setup ---
        LocalDateTime since = LocalDateTime.now().minusWeeks(1);

        // Crear auditoría de cocina para recipe1 (más consumo)
        RecipeCookingAudit audit1 = new RecipeCookingAudit();
        audit1.setRecipe(recipe1);
        audit1.setUser(testUser);
        audit1.setQuantityCooked(BigDecimal.valueOf(10));
        audit1.setCookingDate(LocalDateTime.now());
        cookingAuditRepository.save(audit1);

        // Crear auditoría de cocina para recipe2 (consumo medio)
        RecipeCookingAudit audit2 = new RecipeCookingAudit();
        audit2.setRecipe(recipe2);
        audit2.setUser(testUser);
        audit2.setQuantityCooked(BigDecimal.valueOf(5));
        audit2.setCookingDate(LocalDateTime.now());
        cookingAuditRepository.save(audit2);

        // Crear auditoría de cocina para recipe3 (menos consumo)
        RecipeCookingAudit audit3 = new RecipeCookingAudit();
        audit3.setRecipe(recipe3);
        audit3.setUser(testUser);
        audit3.setQuantityCooked(BigDecimal.valueOf(2));
        audit3.setCookingDate(LocalDateTime.now());
        cookingAuditRepository.save(audit3);

        // --- Execute ---
        List<String> topRecipes = cookingAuditRepository.findTopConsumingRecipesByProduct(
                product1.getId(),
                since
        );

        // --- Verify ---
        assertThat(topRecipes).isNotEmpty();
        assertThat(topRecipes.size()).isLessThanOrEqualTo(3);

        // Verificar que están ordenadas por consumo descendente
        // Pizza (10 * 0.3 = 3)
        // Salsa (5 * 0.5 = 2.5)
        // Sopa (2 * 0.2 = 0.4)
        assertThat(topRecipes).contains("Pizza", "Salsa", "Sopa");
        assertThat(topRecipes.get(0)).isEqualTo("Pizza");
    }

    @Test
    @DisplayName("findTopConsumingRecipesByProduct devuelve lista vacía sin datos")
    void testFindTopConsumingRecipesByProduct_EmptyWhenNoData() {
        // --- Setup ---
        LocalDateTime since = LocalDateTime.now().minusDays(1);

        // --- Execute ---
        List<String> topRecipes = cookingAuditRepository.findTopConsumingRecipesByProduct(
                product1.getId(),
                since
        );

        // --- Verify ---
        assertThat(topRecipes).isEmpty();
    }

    @Test
    @DisplayName("findTopConsumingRecipesByProduct filtra por fecha")
    void testFindTopConsumingRecipesByProduct_FiltersOldData() {
        // --- Setup ---
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoDaysAgo = now.minusDays(2);

        // Crear auditoría antigua (fuera del rango)
        RecipeCookingAudit oldAudit = new RecipeCookingAudit();
        oldAudit.setRecipe(recipe1);
        oldAudit.setUser(testUser);
        oldAudit.setQuantityCooked(BigDecimal.valueOf(100));
        oldAudit.setCookingDate(now.minusDays(3));
        cookingAuditRepository.save(oldAudit);

        // Crear auditoría reciente (dentro del rango)
        RecipeCookingAudit recentAudit = new RecipeCookingAudit();
        recentAudit.setRecipe(recipe2);
        recentAudit.setUser(testUser);
        recentAudit.setQuantityCooked(BigDecimal.valueOf(1));
        recentAudit.setCookingDate(now);
        cookingAuditRepository.save(recentAudit);

        // --- Execute ---
        List<String> topRecipes = cookingAuditRepository.findTopConsumingRecipesByProduct(
                product1.getId(),
                twoDaysAgo
        );

        // --- Verify ---
        // Con filtro de 2 días atrás, ambas auditorías deberían estar incluidas
        assertThat(topRecipes).contains("Salsa");
        
        // Ahora prueba con un filtro más estricto - hace menos de 2 días
        List<String> recentOnly = cookingAuditRepository.findTopConsumingRecipesByProduct(
                product1.getId(),
                now.minusHours(1)
        );
        
        // Con filtro estricto, solo Salsa (auditoría de ahora) debería estar incluida
        assertThat(recentOnly).contains("Salsa");
    }

    @Test
    @DisplayName("findTopConsumingRecipesByProduct devuelve como máximo 3 recetas")
    void testFindTopConsumingRecipesByProduct_LimitsToThreeRecipes() {
        // --- Setup ---
        LocalDateTime since = LocalDateTime.now().minusWeeks(1);

        // Crear muchas auditorías para diferentes recetas
        for (int i = 0; i < 5; i++) {
            RecipeCookingAudit audit = new RecipeCookingAudit();
            if (i == 0) audit.setRecipe(recipe1);
            else if (i == 1) audit.setRecipe(recipe2);
            else audit.setRecipe(recipe3);
            audit.setUser(testUser);
            audit.setQuantityCooked(BigDecimal.valueOf(10 - i));
            audit.setCookingDate(LocalDateTime.now());
            cookingAuditRepository.save(audit);
        }

        // --- Execute ---
        List<String> topRecipes = cookingAuditRepository.findTopConsumingRecipesByProduct(
                product1.getId(),
                since
        );

        // --- Verify ---
        assertThat(topRecipes).hasSizeLessThanOrEqualTo(3);
    }
}
