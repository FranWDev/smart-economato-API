package com.economato.inventory.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.economato.inventory.application.dto.response.ProductResponseDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;

@ExtendWith(MockitoExtension.class)
class CacheWarmupServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private RecipeService recipeService;
    @Mock
    private UserService userService;
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
                List.of(new ProductResponseDTO(1, "Harina", "KG", null, "P1", null, null, false, null)),
                PageRequest.of(0, 10),
                1));
        when(recipeService.findAll(any())).thenReturn(new PageImpl<>(
                List.of(new RecipeResponseDTO(1, "Pan", null, null, null, false, null, List.of(), List.of())),
                PageRequest.of(0, 10),
                1));
        when(userService.findAll(any())).thenReturn(new PageImpl<>(
                List.of(new UserResponseDTO(1, "Admin", "admin", false, false, null, null)),
                PageRequest.of(0, 10),
                1));

        CacheWarmupService warmup = new CacheWarmupService(
                productService,
                recipeService,
                userService,
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
        verify(userService, atLeastOnce()).findAll(any());
        verify(userService, atLeastOnce()).findById(1);

        verify(allergenService).findAll(PageRequest.of(0, 100));
        verify(supplierService).findAll(PageRequest.of(0, 50));
        verify(stockAlertService).getActiveAlerts();
        verify(recipeComponentService).findAll(PageRequest.of(0, 50));

        verify(productService).getProductStats();
        verify(recipeService).getRecipeStats();
        verify(userService).getUserStats();
    }
}
