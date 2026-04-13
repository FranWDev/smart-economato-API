package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.usecase.smg.model.EntityMemory;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntityExtractor {

    private static final String CATALOG_PRODUCTS_KEY = "ai:catalog:products";
    private static final String CATALOG_RECIPES_KEY = "ai:catalog:recipes";

    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiSmgProperties aiSmgProperties;
    private final ObjectMapper objectMapper;

    public EntityMemory extract(List<AiChatMessage> history) {
        EntityMemory memory = new EntityMemory();
        if (history == null || history.isEmpty()) {
            return memory;
        }

        for (AiChatMessage message : history) {
            if (message.getRole() == MessageRole.TOOL) {
                extractFromToolResult(message.getToolResult(), memory);
            }
        }

        Map<String, Integer> productCatalog = loadCatalog(CATALOG_PRODUCTS_KEY, true);
        Map<String, Integer> recipeCatalog = loadCatalog(CATALOG_RECIPES_KEY, false);

        for (AiChatMessage message : history) {
            if (message.getRole() != MessageRole.USER) {
                continue;
            }
            String normalized = normalize(message.getContent());
            if (normalized.isBlank()) {
                continue;
            }

            for (Map.Entry<String, Integer> entry : productCatalog.entrySet()) {
                if (normalized.contains(entry.getKey())) {
                    memory.addProductById(entry.getValue());
                    memory.addProductByName(entry.getKey());
                }
            }

            for (Map.Entry<String, Integer> entry : recipeCatalog.entrySet()) {
                if (normalized.contains(entry.getKey())) {
                    memory.addRecipeById(entry.getValue());
                    memory.addRecipeByName(entry.getKey());
                }
            }
        }

        return memory;
    }

    private void extractFromToolResult(String toolResult, EntityMemory memory) {
        if (toolResult == null || toolResult.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(toolResult);
            traverseNode(node, memory);
        } catch (Exception ex) {
            log.debug("Unable to parse tool result for entity extraction: {}", ex.getMessage());
        }
    }

    private void traverseNode(JsonNode node, EntityMemory memory) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            Integer productId = readInteger(node, "productId");
            if (productId != null) {
                memory.addProductById(productId);
            }

            Integer recipeId = readInteger(node, "recipeId");
            if (recipeId != null) {
                memory.addRecipeById(recipeId);
            }

            Integer orderId = readInteger(node, "orderId");
            if (orderId != null) {
                memory.addOrderById(orderId);
            }

            if (node.has("id") && node.has("name")) {
                Integer id = readInteger(node, "id");
                String name = readString(node, "name");
                if (id != null && name != null) {
                    String normalizedName = normalize(name);
                    if (!normalizedName.isBlank()) {
                        memory.addProductByName(normalizedName);
                        memory.addRecipeByName(normalizedName);
                    }
                }
            }

            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                traverseNode(values.next(), memory);
            }
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                traverseNode(child, memory);
            }
        }
    }

    private Map<String, Integer> loadCatalog(String key, boolean products) {
        Map<Object, Object> cached = stringRedisTemplate.opsForHash().entries(key);
        if (cached != null && !cached.isEmpty()) {
            Map<String, Integer> catalog = new HashMap<>();
            for (Map.Entry<Object, Object> entry : cached.entrySet()) {
                try {
                    catalog.put(String.valueOf(entry.getKey()), Integer.parseInt(String.valueOf(entry.getValue())));
                } catch (NumberFormatException ignored) {
                    log.debug("Skipping malformed catalog entry for key {}", key);
                }
            }
            if (!catalog.isEmpty()) {
                return catalog;
            }
        }

        Map<String, Integer> loaded = new HashMap<>();
        if (products) {
            for (Product product : productRepository.findAllActive()) {
                String normalized = normalize(product.getName());
                if (!normalized.isBlank()) {
                    loaded.put(normalized, product.getId());
                }
            }
        } else {
            for (Recipe recipe : recipeRepository.findAll()) {
                String normalized = normalize(recipe.getName());
                if (!normalized.isBlank()) {
                    loaded.put(normalized, recipe.getId());
                }
            }
        }

        if (!loaded.isEmpty()) {
            Map<String, String> toCache = new HashMap<>();
            loaded.forEach((k, v) -> toCache.put(k, String.valueOf(v)));
            stringRedisTemplate.opsForHash().putAll(key, toCache);
            stringRedisTemplate.expire(key, aiSmgProperties.getCatalogCacheTtlSeconds(), TimeUnit.SECONDS);
        }
        return loaded;
    }

    private Integer readInteger(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String readString(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }
}
