package com.economato.inventory.application.usecase.smg;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.application.dto.mcp.McpSystemContextDto;
import com.economato.inventory.application.usecase.mcp.McpUtilityService;
import com.economato.inventory.application.usecase.smg.model.CompressedContext;
import com.economato.inventory.application.usecase.smg.model.EntityMemory;
import com.economato.inventory.application.usecase.smg.model.OrderSnapshot;
import com.economato.inventory.application.usecase.smg.model.ProductSnapshot;
import com.economato.inventory.application.usecase.smg.model.RecipeSnapshot;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.infrastructure.config.ai.AiIntentProperties;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class SemanticMemoryGraphServiceTest {

    @Mock
    private EntityExtractor entityExtractor;

    @Mock
    private EntityEnricher entityEnricher;

    @Mock
    private McpUtilityService mcpUtilityService;

    private SemanticMemoryGraphService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        AiSmgProperties smgProperties = new AiSmgProperties();
        smgProperties.setTokenBudget(1000);
        smgProperties.setWorkingMemoryWeight(0.50);
        smgProperties.setEntityMemoryWeight(0.20);
        smgProperties.setTopicMemoryWeight(0.10);
        smgProperties.setIntentMemoryWeight(0.10);
        smgProperties.setSystemContextWeight(0.10);
        smgProperties.setDecayLambda(3.0);
        smgProperties.setDecayFullThreshold(0.7);
        smgProperties.setDecayOnelinerThreshold(0.3);
        smgProperties.setMaxWorkingMemoryMessages(5);
        smgProperties.setToolResultMaxChars(120);
        smgProperties.setTokenEstimationDivisor(4);

        AiIntentProperties intentProperties = new AiIntentProperties();

        TokenEstimator tokenEstimator = new TokenEstimator(smgProperties);
        TopicClusterer topicClusterer = new TopicClusterer(smgProperties);
        IntentDetector intentDetector = new IntentDetector(intentProperties);
        DecayFunction decayFunction = new DecayFunction(smgProperties);
        ToolResultCompressor toolResultCompressor = new ToolResultCompressor(smgProperties, new ObjectMapper());

        meterRegistry = new SimpleMeterRegistry();
        service = new SemanticMemoryGraphService(
                tokenEstimator,
                entityExtractor,
                entityEnricher,
                topicClusterer,
                intentDetector,
                decayFunction,
                toolResultCompressor,
                smgProperties,
                mcpUtilityService,
                meterRegistry
        );
    }

    @Test
    void compress_buildsAllLayersAndRecordsMetrics() {
        LocalDateTime base = LocalDateTime.of(2026, 4, 13, 10, 0);
        List<AiChatMessage> history = List.of(
                message(MessageRole.USER, "Necesitamos comprar tomate", null, null, base),
                message(MessageRole.TOOL, null, "create-order", "{" +
                        "\"productId\":1," +
                        "\"empty\":\"\"," +
                        "\"items\":[1,2,3,4,5,6]" +
                        "}", base.plusMinutes(1)),
                message(MessageRole.ASSISTANT, "Pedido creado correctamente", null, null, base.plusMinutes(2))
        );

        EntityMemory memory = new EntityMemory();
        memory.putProductSnapshot(new ProductSnapshot(
                1,
                "Tomate",
                new BigDecimal("10.000"),
                "kg",
                new BigDecimal("2.50"),
                "LOW",
                new BigDecimal("12.000"),
                4
        ));
        memory.putRecipeSnapshot(new RecipeSnapshot(
                2,
                "Salsa",
                new BigDecimal("5.00"),
                List.of("gluten"),
                Map.of(1, new BigDecimal("0.500"))
        ));
        memory.putOrderSnapshot(new OrderSnapshot(
                3,
                "CONFIRMED",
                "Proveedor Central",
                2,
                new BigDecimal("12.00")
        ));

        when(entityExtractor.extract(history)).thenReturn(memory);
        doNothing().when(entityEnricher).enrich(memory);
        when(mcpUtilityService.getSystemContext()).thenReturn(McpSystemContextDto.builder()
                .totalProducts(7)
                .pendingOrdersCount(2)
                .activeAlertsCount(1)
                .totalRecipes(3)
                .build());

        CompressedContext context = service.compress(history, "es");

        assertEquals("es", context.userLanguage());
        assertTrue(context.systemContext().contains("language: es"));
        assertTrue(context.systemContext().contains("total_products: 7"));
        assertTrue(context.intentMemory().contains("[resolved] ORDER_CREATE"));
        assertTrue(context.entityMemory().contains("## Products"));
        assertTrue(context.entityMemory().contains("Tomate"));
        assertTrue(context.entityMemory().contains("(ID:1)"));
        assertTrue(context.entityMemory().contains("price: 2.50"));
        assertTrue(context.entityMemory().contains("## Recipes"));
        assertTrue(context.entityMemory().contains("Salsa"));
        assertTrue(context.topicMemory().contains("ORDER_CREATE"));
        assertTrue(context.workingMemory().stream().anyMatch(message ->
                MessageRole.TOOL.equals(message.role())
                        && message.content().contains("\"items\"")));
        assertTrue(context.totalEstimatedTokens() > 0);
        assertTrue(context.compressionRatio() > 0d);
        assertNotNull(meterRegistry.find("ai.smg.compression.duration").timer());
        assertNotNull(meterRegistry.find("ai.smg.entities.extracted.total").counter());
        assertNotNull(meterRegistry.find("ai.smg.compression.ratio").gauge());

        verify(entityExtractor).extract(history);
        verify(entityEnricher).enrich(memory);
        verify(mcpUtilityService).getSystemContext();
    }

    @Test
    void compress_emptyHistory_returnsMinimalContext() {
        when(mcpUtilityService.getSystemContext()).thenReturn(McpSystemContextDto.builder()
                .totalProducts(0)
                .pendingOrdersCount(0)
                .activeAlertsCount(0)
                .totalRecipes(0)
                .build());

        CompressedContext context = service.compress(List.of(), null);

        assertEquals("-", context.intentMemory());
        assertEquals("-", context.entityMemory());
        assertEquals("-", context.topicMemory());
                assertTrue(context.systemContext().contains("language: es"));
                assertTrue(context.systemContext().contains("total_products: 0"));
        assertTrue(context.workingMemory().isEmpty());
        verify(mcpUtilityService).getSystemContext();
    }

    private AiChatMessage message(MessageRole role, String content, String toolName, String toolResult, LocalDateTime createdAt) {
        return AiChatMessage.builder()
                .role(role)
                .content(content)
                .toolName(toolName)
                .toolResult(toolResult)
                .createdAt(createdAt)
                .build();
    }
}