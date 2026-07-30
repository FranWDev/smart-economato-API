package com.economato.inventory.application.usecase.shared;
import com.economato.inventory.application.usecase.product.ProductService;
import com.economato.inventory.application.usecase.product.SupplierService;
import com.economato.inventory.application.usecase.recipe.AllergenService;
import com.economato.inventory.application.usecase.recipe.RecipeComponentService;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.application.usecase.stock.StockAlertService;
import com.economato.inventory.application.dto.recipe.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.product.response.ProductResponseDTO;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiSmgProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


/**
 * Servicio para calentar la caché al iniciar la aplicación.
 * Precarga productos, recetas y usuarios de forma asíncrona durante el startup.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class CacheWarmupService implements CommandLineRunner {

    private final ProductService productService;
    private final RecipeService recipeService;
    private final AllergenService allergenService;
    private final SupplierService supplierService;
    private final StockAlertService stockAlertService;
    private final RecipeComponentService recipeComponentService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final AiSmgProperties aiSmgProperties;

    @Override
    public void run(String... args) throws Exception {
        log.info("Iniciando warmup de caché...");
        long startTime = System.currentTimeMillis();

        try {

            CompletableFuture<Void> productsWarmup = CompletableFuture.runAsync(this::warmupProducts);
            CompletableFuture<Void> recipesWarmup = CompletableFuture.runAsync(this::warmupRecipes);
            CompletableFuture<Void> allergensWarmup = CompletableFuture.runAsync(this::warmupAllergens);
            CompletableFuture<Void> suppliersWarmup = CompletableFuture.runAsync(this::warmupSuppliers);
            CompletableFuture<Void> alertsWarmup = CompletableFuture.runAsync(this::warmupAlerts);
            CompletableFuture<Void> statsWarmup = CompletableFuture.runAsync(this::warmupStats);
            CompletableFuture<Void> recipeComponentsWarmup = CompletableFuture.runAsync(this::warmupRecipeComponents);
            CompletableFuture<Void> aiCatalogWarmup = CompletableFuture.runAsync(this::warmupAiCatalogs);

            CompletableFuture.allOf(productsWarmup, recipesWarmup, allergensWarmup,
                    suppliersWarmup, alertsWarmup, statsWarmup, recipeComponentsWarmup, aiCatalogWarmup)
                    .get(60, TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Warmup de caché completado en {}ms", duration);
            log.info("Sistema listo para recibir peticiones con caché pre-cargado");

        } catch (Exception e) {
            log.warn("Error durante warmup de caché (no crítico): {}", e.getMessage());
            log.info("Sistema continuará con caché vacío (se llenará con las primeras peticiones)");
        }
    }

    private void warmupProducts() {
        try {
            log.info("Pre-cargando productos...");

            Pageable page1 = PageRequest.of(0, 10);
            Page<ProductResponseDTO> products1 = productService.findAll(page1);

            Pageable page2 = PageRequest.of(1, 10);
            productService.findAll(page2);

            products1.stream()
                    .limit(10)
                    .forEach(product -> {
                        try {
                            productService.findById(product.getId());
                        } catch (Exception e) {
                            log.warn("Error cargando detalle de producto {}: {}", product.getId(), e.getMessage());
                        }
                    });

            log.info("Productos pre-cargados (2 páginas + 10 detalles)");
        } catch (Exception e) {
            log.warn("Error pre-cargando productos: {}", e.getMessage());
        }
    }

    private void warmupRecipes() {
        try {
            log.info("Pre-cargando recetas...");

            Pageable page1 = PageRequest.of(0, 10);
            Page<RecipeResponseDTO> recipes1 = recipeService.findAll(page1);

            Pageable page2 = PageRequest.of(1, 10);
            recipeService.findAll(page2);

            recipes1.stream()
                    .limit(5)
                    .forEach(recipe -> {
                        try {
                            recipeService.findById(recipe.getId());
                        } catch (Exception e) {
                            log.warn("Error cargando detalle de receta {}: {}", recipe.getId(), e.getMessage());
                        }
                    });

            log.info("Recetas pre-cargadas (2 páginas + 5 detalles)");
        } catch (Exception e) {
            log.warn("Error pre-cargando recetas: {}", e.getMessage());
        }
    }

    private void warmupAllergens() {
        try {
            log.info("Pre-cargando alérgenos...");
            allergenService.findAll(PageRequest.of(0, 100));
            log.info("Alérgenos pre-cargados");
        } catch (Exception e) {
            log.warn("Error pre-cargando alérgenos: {}", e.getMessage());
        }
    }

    private void warmupSuppliers() {
        try {
            log.info("Pre-cargando proveedores...");
            supplierService.findAll(PageRequest.of(0, 50));
            log.info("Proveedores pre-cargados");
        } catch (Exception e) {
            log.warn("Error pre-cargando proveedores: {}", e.getMessage());
        }
    }

    private void warmupAlerts() {
        try {
            log.info("Pre-cargando alertas predictivas...");
            stockAlertService.getActiveAlerts();
            log.info("Alertas predictivas pre-cargadas");
        } catch (Exception e) {
            log.warn("Error pre-cargando alertas: {}", e.getMessage());
        }
    }

    private void warmupStats() {
        try {
            log.info("Pre-cargando estadísticas...");
            productService.getProductStats();
            recipeService.getRecipeStats();
            log.info("Estadísticas pre-cargadas");
        } catch (Exception e) {
            log.warn("Error pre-cargando estadísticas: {}", e.getMessage());
        }
    }

    private void warmupRecipeComponents() {
        try {
            log.info("Pre-cargando componentes de receta...");
            recipeComponentService.findAll(PageRequest.of(0, 50));
            log.info("Componentes de receta pre-cargados");
        } catch (Exception e) {
            log.warn("Error pre-cargando componentes de receta: {}", e.getMessage());
        }
    }

    private void warmupAiCatalogs() {
        try {
            log.info("Pre-cargando catalogos AI en Redis...");

            Map<String, String> productCatalog = new HashMap<>();
            for (Product product : productRepository.findAllActive()) {
                String normalizedName = normalizeName(product.getName());
                if (!normalizedName.isBlank()) {
                    productCatalog.put(normalizedName, String.valueOf(product.getId()));
                }
            }

            Map<String, String> recipeCatalog = new HashMap<>();
            for (Recipe recipe : recipeRepository.findAll()) {
                String normalizedName = normalizeName(recipe.getName());
                if (!normalizedName.isBlank()) {
                    recipeCatalog.put(normalizedName, String.valueOf(recipe.getId()));
                }
            }

            if (!productCatalog.isEmpty()) {
                String productsKey = "ai:catalog:products";
                stringRedisTemplate.delete(productsKey);
                stringRedisTemplate.opsForHash().putAll(productsKey, productCatalog);
                stringRedisTemplate.expire(productsKey, aiSmgProperties.getCatalogCacheTtlSeconds(), TimeUnit.SECONDS);
            }

            if (!recipeCatalog.isEmpty()) {
                String recipesKey = "ai:catalog:recipes";
                stringRedisTemplate.delete(recipesKey);
                stringRedisTemplate.opsForHash().putAll(recipesKey, recipeCatalog);
                stringRedisTemplate.expire(recipesKey, aiSmgProperties.getCatalogCacheTtlSeconds(), TimeUnit.SECONDS);
            }

            log.info("Catalogos AI pre-cargados: products={}, recipes={}", productCatalog.size(), recipeCatalog.size());
        } catch (Exception e) {
            log.warn("Error pre-cargando catalogos AI: {}", e.getMessage());
        }
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }
}
