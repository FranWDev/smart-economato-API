package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.dto.response.StockAlertDTO;
import com.economato.inventory.application.usecase.ProductBatchService;
import com.economato.inventory.application.usecase.StockAlertService;
import com.economato.inventory.application.usecase.smg.model.EntityMemory;
import com.economato.inventory.application.usecase.smg.model.OrderSnapshot;
import com.economato.inventory.application.usecase.smg.model.ProductSnapshot;
import com.economato.inventory.application.usecase.smg.model.RecipeSnapshot;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeComponent;
import com.economato.inventory.domain.model.StockPrediction;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockPredictionRepository;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntityEnricher {

    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final OrderRepository orderRepository;
    private final StockPredictionRepository stockPredictionRepository;
    private final StockAlertService stockAlertService;
    private final ProductBatchService productBatchService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiSmgProperties aiSmgProperties;
    private final ObjectMapper objectMapper;

    public void enrich(EntityMemory memory) {
        enrichProducts(memory);
        enrichRecipes(memory);
        enrichOrders(memory);
    }

    private void enrichProducts(EntityMemory memory) {
        for (Integer productId : new ArrayList<>(memory.getProductIds())) {
            if (productId == null) {
                continue;
            }

            String cacheKey = "ai:entity:product:" + productId;
            ProductSnapshot cached = readCachedProductSnapshot(cacheKey);
            if (cached != null) {
                memory.putProductSnapshot(cached);
                continue;
            }

            Optional<Product> productOpt = productRepository.findByIdWithSupplier(productId);
            if (productOpt.isEmpty()) {
                continue;
            }

            Product product = productOpt.get();
            Optional<StockPrediction> predictionOpt = stockPredictionRepository.findById(productId);
            Optional<StockAlertDTO> alertOpt = stockAlertService.getAlertByProductId(productId);
            List<ProductBatch> batches = productBatchService.getActiveBatches(productId);

            Integer daysToExpiry = null;
            if (!batches.isEmpty() && batches.get(0).getExpirationDate() != null) {
                daysToExpiry = (int) ChronoUnit.DAYS.between(LocalDate.now(), batches.get(0).getExpirationDate());
            }

            ProductSnapshot snapshot = new ProductSnapshot(
                    product.getId(),
                    product.getName(),
                    product.getCurrentStock(),
                    product.getUnit(),
                    product.getUnitPrice(),
                    alertOpt.map(a -> a.getSeverity().name()).orElse(null),
                    predictionOpt.map(StockPrediction::getProjectedConsumption).orElse(null),
                    daysToExpiry
            );

            memory.putProductSnapshot(snapshot);
            cacheProductSnapshot(cacheKey, snapshot);
        }
    }

    private void enrichRecipes(EntityMemory memory) {
        for (Integer recipeId : new ArrayList<>(memory.getRecipeIds())) {
            if (recipeId == null) {
                continue;
            }

            Optional<Recipe> recipeOpt = recipeRepository.findByIdWithDetails(recipeId);
            if (recipeOpt.isEmpty()) {
                continue;
            }

            Recipe recipe = recipeOpt.get();
            Map<Integer, BigDecimal> needs = new HashMap<>();
            for (RecipeComponent component : recipe.getComponents()) {
                if (component.getProduct() != null && component.getProduct().getId() != null) {
                    needs.put(component.getProduct().getId(), component.getQuantity());
                }
            }

            List<String> allergens = recipe.getAllergens().stream()
                    .map(a -> a.getName())
                    .toList();

            RecipeSnapshot snapshot = new RecipeSnapshot(
                    recipe.getId(),
                    recipe.getName(),
                    recipe.getTotalCost(),
                    allergens,
                    needs
            );
            memory.putRecipeSnapshot(snapshot);
        }
    }

    private void enrichOrders(EntityMemory memory) {
        for (Integer orderId : new ArrayList<>(memory.getOrderIds())) {
            if (orderId == null) {
                continue;
            }

            Optional<Order> orderOpt = orderRepository.findByIdWithDetails(orderId);
            if (orderOpt.isEmpty()) {
                continue;
            }

            Order order = orderOpt.get();
            BigDecimal total = order.getDetails().stream()
                    .map(d -> {
                        if (d.getProduct() == null || d.getProduct().getUnitPrice() == null || d.getQuantity() == null) {
                            return BigDecimal.ZERO;
                        }
                        return d.getProduct().getUnitPrice().multiply(d.getQuantity());
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            OrderSnapshot snapshot = new OrderSnapshot(
                    order.getId(),
                    order.getStatus() != null ? order.getStatus().name() : "-",
                    order.getSupplier() != null ? order.getSupplier().getName() : "-",
                    order.getDetails() != null ? order.getDetails().size() : 0,
                    total
            );
            memory.putOrderSnapshot(snapshot);
        }
    }

    private ProductSnapshot readCachedProductSnapshot(String cacheKey) {
        try {
            String value = stringRedisTemplate.opsForValue().get(cacheKey);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, ProductSnapshot.class);
        } catch (Exception ex) {
            log.debug("Unable to read cached product snapshot: {}", ex.getMessage());
            return null;
        }
    }

    private void cacheProductSnapshot(String cacheKey, ProductSnapshot snapshot) {
        try {
            String value = objectMapper.writeValueAsString(snapshot);
            stringRedisTemplate.opsForValue().set(cacheKey, value, aiSmgProperties.getEntityCacheTtlSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.debug("Unable to cache product snapshot: {}", ex.getMessage());
        }
    }
}
