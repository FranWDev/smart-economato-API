package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.usecase.smg.model.EntityMemory;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityExtractorTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EntityExtractor entityExtractor;

    @BeforeEach
    void setUp() {
        AiSmgProperties properties = new AiSmgProperties();
        properties.setCatalogCacheTtlSeconds(300);

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        entityExtractor = new EntityExtractor(productRepository, recipeRepository, stringRedisTemplate, properties, objectMapper);
    }

    @Test
    void extract_parsesToolResultsAndMatchesCatalogNames() throws Exception {
        Product product = new Product();
        product.setId(42);
        product.setName("Aceite de Oliva");

        Recipe recipe = new Recipe();
        recipe.setId(77);
        recipe.setName("Tomate Frito");

        when(hashOperations.entries("ai:catalog:products")).thenReturn(Collections.emptyMap());
        when(hashOperations.entries("ai:catalog:recipes")).thenReturn(Collections.emptyMap());
        when(productRepository.findAllActive()).thenReturn(List.of(product));
        when(recipeRepository.findAll()).thenReturn(List.of(recipe));

        String toolResult = "{" +
                "\"productId\":7," +
                "\"recipeId\":9," +
                "\"orderId\":11," +
                "\"items\":[{" +
                "\"id\":123," +
                "\"name\":\"Salsa de Tomate\"" +
                "}]" +
                "}";

        List<AiChatMessage> history = List.of(
                message(MessageRole.USER, "Necesito aceite de oliva y tomate frito", null),
                message(MessageRole.TOOL, null, toolResult)
        );

        EntityMemory memory = entityExtractor.extract(history);

        assertTrue(memory.getProductIds().containsAll(List.of(7, 42)));
        assertTrue(memory.getRecipeIds().containsAll(List.of(9, 77)));
        assertTrue(memory.getOrderIds().contains(11));
        assertTrue(memory.getProductNames().contains("aceite de oliva"));
        assertTrue(memory.getRecipeNames().contains("tomate frito"));
        assertTrue(memory.getProductNames().contains("salsa de tomate"));
        assertTrue(memory.getRecipeNames().contains("salsa de tomate"));

        verify(hashOperations).putAll(eq("ai:catalog:products"), anyMap());
        verify(hashOperations).putAll(eq("ai:catalog:recipes"), anyMap());
        verify(stringRedisTemplate).expire(eq("ai:catalog:products"), eq(300L), eq(TimeUnit.SECONDS));
        verify(stringRedisTemplate).expire(eq("ai:catalog:recipes"), eq(300L), eq(TimeUnit.SECONDS));
    }

    private AiChatMessage message(MessageRole role, String content, String toolResult) {
        return AiChatMessage.builder()
                .role(role)
                .content(content)
                .toolResult(toolResult)
                .createdAt(LocalDateTime.now())
                .build();
    }
}