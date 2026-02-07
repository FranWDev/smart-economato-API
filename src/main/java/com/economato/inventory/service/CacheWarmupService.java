package com.economato.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.economato.inventory.dto.response.RecipeResponseDTO;
import com.economato.inventory.dto.response.UserResponseDTO;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class CacheWarmupService implements CommandLineRunner {

    private final ProductService productService;
    private final RecipeService recipeService;
    private final UserService userService;

    @Override
    public void run(String... args) throws Exception {
        log.info("Iniciando warmup de caché...");
        long startTime = System.currentTimeMillis();

        try {

            CompletableFuture<Void> productsWarmup = CompletableFuture.runAsync(this::warmupProducts);
            CompletableFuture<Void> recipesWarmup = CompletableFuture.runAsync(this::warmupRecipes);
            CompletableFuture<Void> usersWarmup = CompletableFuture.runAsync(this::warmupUsers);

            CompletableFuture.allOf(productsWarmup, recipesWarmup, usersWarmup)
                    .get(30, TimeUnit.SECONDS);

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
            productService.findAll(page1);

            Pageable page2 = PageRequest.of(1, 10);
            productService.findAll(page2);

            log.info("Productos pre-cargados (2 páginas, 20 items)");
        } catch (Exception e) {
            log.warn("Error pre-cargando productos: {}", e.getMessage());
        }
    }

    private void warmupRecipes() {
        try {
            log.info("Pre-cargando recetas...");

            Pageable page1 = PageRequest.of(0, 10);
            List<RecipeResponseDTO> recipes1 = recipeService.findAll(page1);

            Pageable page2 = PageRequest.of(1, 10);
            recipeService.findAll(page2);

            recipes1.stream()
                    .limit(5)
                    .forEach(recipe -> {
                        try {
                            recipeService.findById(recipe.getId());
                        } catch (Exception e) {

                        }
                    });

            log.info("Recetas pre-cargadas (2 páginas + 5 detalles)");
        } catch (Exception e) {
            log.warn("Error pre-cargando recetas: {}", e.getMessage());
        }
    }

    private void warmupUsers() {
        try {
            log.info("Pre-cargando usuarios...");

            Pageable page1 = PageRequest.of(0, 10);
            List<UserResponseDTO> users = userService.findAll(page1);

            users.stream()
                    .limit(3)
                    .forEach(user -> {
                        try {
                            userService.findById(user.getId());
                        } catch (Exception e) {

                        }
                    });

            log.info("Usuarios pre-cargados (1 página + 3 detalles)");
        } catch (Exception e) {
            log.warn("Error pre-cargando usuarios: {}", e.getMessage());
        }
    }
}
