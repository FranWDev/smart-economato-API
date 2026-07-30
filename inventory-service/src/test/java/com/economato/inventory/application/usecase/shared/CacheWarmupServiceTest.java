package com.economato.inventory.application.usecase.shared;

import com.economato.inventory.application.dto.product.response.ProductResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeResponseDTO;
import com.economato.inventory.application.usecase.product.ProductService;
import com.economato.inventory.application.usecase.product.SupplierService;
import com.economato.inventory.application.usecase.recipe.AllergenService;
import com.economato.inventory.application.usecase.recipe.RecipeComponentService;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.application.usecase.stock.StockAlertService;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiSmgProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheWarmupServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private RecipeService recipeService;
    @Mock
    private AllergenService allergenService;
    @Mock
    private SupplierService supplierService;
    @Mock
    private StockAlertService stockAlertService;
    @Mock
    private RecipeComponentService recipeComponentService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private AiSmgProperties aiSmgProperties;

    @Test
    void run_executesExpandedWarmupFlows() throws Exception {
        when(productService.findAll(any())).thenReturn(new PageImpl<>(
                List.of(new ProductResponseDTO(1, "Harina", "KG", null, "P1", null, null, new BigDecimal("1.000"), false, null)),
                PageRequest.of(0, 10),
                1));
        when(recipeService.findAll(any())).thenReturn(new PageImpl<>(
                List.of(new RecipeResponseDTO(1, "Pan", null, null, null, null, false, null, List.of(), List.of())),
                PageRequest.of(0, 10),
                1));

        CacheWarmupService warmup = new CacheWarmupService(
                productService,
                recipeService,
                allergenService,
                supplierService,
                stockAlertService,
                recipeComponentService,
                stringRedisTemplate,
                productRepository,
                recipeRepository,
                aiSmgProperties);

        warmup.run();

        verify(productService, atLeastOnce()).findAll(any());
        verify(productService, atLeastOnce()).findById(1);
        verify(recipeService, atLeastOnce()).findAll(any());
        verify(recipeService, atLeastOnce()).findById(1);

        verify(allergenService).findAll(PageRequest.of(0, 100));
        verify(supplierService).findAll(PageRequest.of(0, 50));
        verify(stockAlertService).getActiveAlerts();
        verify(recipeComponentService).findAll(PageRequest.of(0, 50));

        verify(productService).getProductStats();
        verify(recipeService).getRecipeStats();
    }
}
