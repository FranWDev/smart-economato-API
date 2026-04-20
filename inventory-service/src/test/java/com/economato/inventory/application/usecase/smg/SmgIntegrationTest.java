package com.economato.inventory.application.usecase.smg;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.economato.inventory.application.dto.mcp.McpSystemContextDto;
import com.economato.inventory.application.usecase.mcp.McpUtilityService;
import com.economato.inventory.application.usecase.smg.model.CompressedContext;
import com.economato.inventory.application.usecase.smg.model.EntityMemory;
import com.economato.inventory.application.usecase.smg.model.ProductSnapshot;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmgIntegrationTest {

    @Mock
    private TokenEstimator tokenEstimator;
    @Mock
    private EntityExtractor entityExtractor;
    @Mock
    private EntityEnricher entityEnricher;
    @Mock
    private TopicClusterer topicClusterer;
    @Mock
    private IntentDetector intentDetector;
    @Mock
    private DecayFunction decayFunction;
    @Mock
    private ToolResultCompressor toolResultCompressor;
    @Mock
    private McpUtilityService mcpUtilityService;

    private SemanticMemoryGraphService semanticMemoryGraphService;

    @BeforeEach
    void setUp() {
        AiSmgProperties aiSmgProperties = new AiSmgProperties();
        semanticMemoryGraphService = new SemanticMemoryGraphService(
                tokenEstimator,
                entityExtractor,
                entityEnricher,
                topicClusterer,
                intentDetector,
                decayFunction,
                toolResultCompressor,
                aiSmgProperties,
                mcpUtilityService,
                new SimpleMeterRegistry()
        );

        when(tokenEstimator.estimate(any())).thenReturn(10);
        when(tokenEstimator.estimateMessages(any())).thenReturn(15);
        when(entityExtractor.extract(any())).thenReturn(new EntityMemory());
        when(topicClusterer.cluster(any())).thenReturn(List.of());
        when(intentDetector.detect(any())).thenReturn(List.of());
        when(mcpUtilityService.getSystemContext()).thenReturn(McpSystemContextDto.builder()
            .totalProducts(10)
            .pendingOrdersCount(2)
            .activeAlertsCount(1)
            .totalRecipes(5)
            .build());
    }

    @Test
    void compress_realHistory_producesValidContext() {
        List<AiChatMessage> history = List.of(
                AiChatMessage.builder().role(MessageRole.USER).content("stock tomate").createdAt(LocalDateTime.now().minusMinutes(2)).build(),
                AiChatMessage.builder().role(MessageRole.ASSISTANT).content("hay 12kg").createdAt(LocalDateTime.now().minusMinutes(1)).build()
        );

        CompressedContext context = semanticMemoryGraphService.compress(history, "es");

        assertNotNull(context);
        assertNotNull(context.systemContext());
        assertTrue(context.systemContext().contains("language: es"));
        assertNotNull(context.workingMemory());
        assertFalse(context.workingMemory().isEmpty());
        assertTrue(context.compressionRatio() > 0);
    }

    @Test
    void compress_withEntityEnrichment_loadsCurrentState() {
        EntityMemory memory = new EntityMemory();
        memory.addProductById(42);
        when(entityExtractor.extract(any())).thenReturn(memory);
        doAnswer(invocation -> {
            EntityMemory target = invocation.getArgument(0);
            target.putProductSnapshot(new ProductSnapshot(
                    42,
                    "Tomate",
                    new BigDecimal("50.000"),
                    "kg",
                    new BigDecimal("2.50"),
                    "LOW",
                    new BigDecimal("12.00"),
                    7
            ));
            return null;
        }).when(entityEnricher).enrich(any(EntityMemory.class));

        List<AiChatMessage> history = List.of(
                AiChatMessage.builder().role(MessageRole.TOOL).toolName("get-product").toolResult("{\"productId\":42}").createdAt(LocalDateTime.now()).build()
        );

        CompressedContext context = semanticMemoryGraphService.compress(history, "es");

        assertTrue(context.entityMemory().contains("## Products"));
        assertTrue(context.entityMemory().contains("50.000"));
        assertTrue(context.entityMemory().contains("Tomate"));
        assertTrue(context.entityMemory().contains("[ALERT: LOW]"));
    }

    @Test
    void compress_emptyHistory_returnsSystemContextOnly() {
        CompressedContext context = semanticMemoryGraphService.compress(List.of(), "es");

        assertNotNull(context.systemContext());
        assertEquals("-", context.intentMemory());
        assertEquals("-", context.entityMemory());
        assertEquals("-", context.topicMemory());
        assertTrue(context.workingMemory().isEmpty());
    }
}
