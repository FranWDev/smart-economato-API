package com.economato.inventory.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeComponent;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockSnapshotRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecipeServiceFefoIntegrationTest {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private ProductBatchService productBatchService;

    @Autowired
    private ProductBatchRepository productBatchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockLedgerRepository stockLedgerRepository;

    @Autowired
    private StockSnapshotRepository stockSnapshotRepository;

    private Product product;
    private Recipe recipe;
    private User user;

    @BeforeEach
    void setUp() {
        productBatchRepository.deleteAll();
        stockLedgerRepository.deleteAll();
        stockSnapshotRepository.deleteAll();
        recipeRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setName("fefo-user");
        user.setUser("fefo-login");
        user.setPassword("secret");
        user.setRole(Role.ADMIN);
        user = userRepository.saveAndFlush(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getName(), null, List.of()));

        product = new Product();
        product.setName("Harina FEFO");
        product.setType("Ingrediente");
        product.setUnit("KG");
        product.setUnitPrice(new BigDecimal("1.00"));
        product.setProductCode("FEFO-ING-1");
        product.setCurrentStock(BigDecimal.ZERO);
        product.setMinimumStock(BigDecimal.ZERO);
        product = productRepository.saveAndFlush(product);

        recipe = new Recipe();
        recipe.setName("Receta FEFO");
        recipe.setPresentation("presentacion");
        recipe.setElaboration("elaboracion");
        recipe.setTotalCost(new BigDecimal("1.00"));

        RecipeComponent component = new RecipeComponent();
        component.setProduct(product);
        component.setQuantity(new BigDecimal("4.000"));
        recipe.addComponent(component);

        recipe = recipeRepository.saveAndFlush(recipe);
    }

    @Test
    @DisplayName("Debe consumir primero el lote con caducidad más próxima al cocinar")
    void cookRecipe_shouldConsumeUsingFefo() {
        StockLedger tx1 = stockLedgerService.recordStockMovement(
                product.getId(),
                new BigDecimal("10.000"),
                MovementType.ENTRADA,
                "Entrada lote cercano",
                user,
                null,
                LocalDate.now().plusDays(2));
        productBatchService.createBatch(product, new BigDecimal("10.000"), LocalDate.now().plusDays(2), tx1);

        StockLedger tx2 = stockLedgerService.recordStockMovement(
                product.getId(),
                new BigDecimal("10.000"),
                MovementType.ENTRADA,
                "Entrada lote lejano",
                user,
                null,
                LocalDate.now().plusDays(10));
        productBatchService.createBatch(product, new BigDecimal("10.000"), LocalDate.now().plusDays(10), tx2);

        RecipeCookingRequestDTO request = new RecipeCookingRequestDTO();
        request.setRecipeId(recipe.getId());
        request.setQuantity(new BigDecimal("3.000"));

        RecipeResponseDTO response = recipeService.cookRecipe(request);
        assertNotNull(response);

        List<ProductBatch> active = productBatchRepository.findActiveByProductIdOrderByExpiration(product.getId());
        assertEquals(1, active.size());
        assertEquals(LocalDate.now().plusDays(10), active.get(0).getExpirationDate());
        assertEquals(new BigDecimal("8.000"), active.get(0).getRemainingQuantity());
    }
}
