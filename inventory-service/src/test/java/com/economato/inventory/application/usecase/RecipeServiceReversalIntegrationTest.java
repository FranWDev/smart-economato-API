package com.economato.inventory.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeComponent;
import com.economato.inventory.domain.model.RecipeCookingAudit;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecipeServiceReversalIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private ProductBatchRepository productBatchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecipeCookingAuditRepository recipeCookingAuditRepository;

    private Product product;
    private Recipe recipe;
    private User user;

    @BeforeEach
    void setUp() {
        clearDatabase();

        user = new User();
        user.setName("reversal-user");
        user.setUser("reversal-login");
        user.setPassword("secret");
        user.setRole(Role.ADMIN);
        user = userRepository.saveAndFlush(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getName(), null, List.of()));

        product = new Product();
        product.setName("Product for Reversal");
        product.setType("Ingredient");
        product.setUnit("KG");
        product.setUnitPrice(new BigDecimal("10.00"));
        product.setProductCode("REV-001");
        product.setCurrentStock(BigDecimal.ZERO);
        product.setMinimumStock(BigDecimal.ZERO);
        product = productRepository.saveAndFlush(product);

        recipe = new Recipe();
        recipe.setName("Recipe for Reversal");
        recipe.setPresentation("presentation");
        recipe.setElaboration("elaboration");
        recipe.setTotalCost(new BigDecimal("10.00"));

        RecipeComponent component = new RecipeComponent();
        component.setProduct(product);
        component.setQuantity(new BigDecimal("1.000"));
        recipe.addComponent(component);

        recipe = recipeRepository.saveAndFlush(recipe);
    }

    @Test
    @DisplayName("Reverting recipe cooking should restore stock to original batches and reactivate them if depleted")
    void revertCooking_shouldRestoreStockToOriginalBatches() {
        // 1. Create two batches, one small enough to be depleted
        ProductBatch batch1 = ProductBatch.builder()
                .product(product)
                .receivedAt(java.time.LocalDateTime.now())
                .expirationDate(LocalDate.now().plusDays(5))
                .initialQuantity(new BigDecimal("2.000"))
                .remainingQuantity(new BigDecimal("2.000"))
                .depleted(false)
                .build();
        batch1 = productBatchRepository.saveAndFlush(batch1);

        ProductBatch batch2 = ProductBatch.builder()
                .product(product)
                .receivedAt(java.time.LocalDateTime.now())
                .expirationDate(LocalDate.now().plusDays(10))
                .initialQuantity(new BigDecimal("5.000"))
                .remainingQuantity(new BigDecimal("5.000"))
                .depleted(false)
                .build();
        batch2 = productBatchRepository.saveAndFlush(batch2);

        product.setCurrentStock(new BigDecimal("7.000"));
        productRepository.saveAndFlush(product);

        // 2. Cook recipe - should consume 3 units (all of batch1 + 1 unit of batch2)
        RecipeCookingRequestDTO cookRequest = new RecipeCookingRequestDTO();
        cookRequest.setRecipeId(recipe.getId());
        cookRequest.setQuantity(new BigDecimal("3.000"));
        
        RecipeResponseDTO response = recipeService.cookRecipe(cookRequest);
        assertNotNull(response);

        // Manually create audit since Kafka aspect is disabled in 'test' profile
        RecipeCookingAudit manualAudit = new RecipeCookingAudit();
        manualAudit.setRecipe(recipe);
        manualAudit.setUser(user);
        manualAudit.setQuantityCooked(cookRequest.getQuantity());
        manualAudit.setDetails("Manual test audit");
        manualAudit.setCookingDate(java.time.LocalDateTime.now());
        manualAudit.setCorrelationId(cookRequest.getCorrelationId());
        recipeCookingAuditRepository.saveAndFlush(manualAudit);

        // Verify batch state after cooking
        ProductBatch updatedBatch1 = productBatchRepository.findById(batch1.getId()).orElseThrow();
        ProductBatch updatedBatch2 = productBatchRepository.findById(batch2.getId()).orElseThrow();
        
        assertEquals(BigDecimal.ZERO.setScale(3), updatedBatch1.getRemainingQuantity());
        assertEquals(true, updatedBatch1.isDepleted());
        assertEquals(new BigDecimal("4.000").setScale(3), updatedBatch2.getRemainingQuantity());
        assertEquals(false, updatedBatch2.isDepleted());

        // 3. Revert cooking
        recipeService.revertCooking(manualAudit.getId(), "Test reversal");
 
        // Verify audit is deleted
        assertFalse(recipeCookingAuditRepository.existsById(manualAudit.getId()), "Audit should be deleted after reversal");

        // 4. Verify original batches are restored
        ProductBatch restoredBatch1 = productBatchRepository.findById(batch1.getId()).orElseThrow();
        ProductBatch restoredBatch2 = productBatchRepository.findById(batch2.getId()).orElseThrow();

        assertEquals(new BigDecimal("2.000").setScale(3), restoredBatch1.getRemainingQuantity());
        assertFalse(restoredBatch1.isDepleted(), "Batch 1 should be reactivated after reversal");
        assertEquals(new BigDecimal("5.000").setScale(3), restoredBatch2.getRemainingQuantity());
        assertFalse(restoredBatch2.isDepleted());
        
        // Verify current stock
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(new BigDecimal("7.000").setScale(3), updatedProduct.getCurrentStock());
    }
}
