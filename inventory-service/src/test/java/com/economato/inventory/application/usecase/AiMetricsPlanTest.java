package com.economato.inventory.application.usecase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.mcp.McpSystemContextDto;
import com.economato.inventory.application.dto.mcp.NestCompletionRequest;
import com.economato.inventory.application.usecase.mcp.McpUtilityService;
import com.economato.inventory.application.usecase.smg.DecayFunction;
import com.economato.inventory.application.usecase.smg.EntityEnricher;
import com.economato.inventory.application.usecase.smg.EntityExtractor;
import com.economato.inventory.application.usecase.smg.IntentDetector;
import com.economato.inventory.application.usecase.smg.SemanticMemoryGraphService;
import com.economato.inventory.application.usecase.smg.TokenEstimator;
import com.economato.inventory.application.usecase.smg.ToolResultCompressor;
import com.economato.inventory.application.usecase.smg.TopicClusterer;
import com.economato.inventory.application.usecase.smg.model.EntityMemory;
import com.economato.inventory.domain.model.AiChat;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.AiChatStatus;
import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.domain.model.UserApiKey;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AiChatRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserApiKeyRepository;
import com.economato.inventory.infrastructure.config.ai.AiChatProperties;
import com.economato.inventory.infrastructure.config.ai.AiIntentProperties;
import com.economato.inventory.infrastructure.config.ai.AiNestProperties;
import com.economato.inventory.infrastructure.config.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.ai.AiRateLimitProperties;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import com.economato.inventory.infrastructure.config.ai.AiVaultProperties;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiMetricsPlanTest {

    @Mock private UserApiKeyRepository userApiKeyRepository;
    @Mock private AiChatRepository aiChatRepository;
    @Mock private AiChatMessageRepository aiChatMessageRepository;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private AuditEventProducer auditEventProducer;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock private CircuitBreaker circuitBreaker;
    @Mock private RestClient nestRestClient;
    @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private RestClient.RequestBodySpec requestBodySpec;

    private SimpleMeterRegistry meterRegistry;
    private AiRateLimitProperties rateLimitProperties;
    private AiProviderProperties providerProperties;
    private AiVaultProperties vaultProperties;
    private User currentUser;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        rateLimitProperties = new AiRateLimitProperties();
        rateLimitProperties.setMessagesPerMinute(10);
        rateLimitProperties.setMaxChatsPerUser(5);
        rateLimitProperties.setMaxMessagesPerChat(50);
        rateLimitProperties.setMaxApiKeysPerUser(5);
        rateLimitProperties.setFailOpen(true);

        providerProperties = new AiProviderProperties();
        AiProviderProperties.ProviderConfig openAiCfg = new AiProviderProperties.ProviderConfig();
        openAiCfg.setEnabled(true);
        openAiCfg.setModelDefault("gpt-4o");
        openAiCfg.setKeyPrefix("sk-");
        providerProperties.setConfigs(Map.of("OPENAI", openAiCfg));

        vaultProperties = new AiVaultProperties();
        vaultProperties.setMasterKey("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        vaultProperties.setCurrentKeyVersion(1);

        currentUser = new User();
        currentUser.setId(10);
        currentUser.setName("Admin");
        when(securityContextHelper.getCurrentUser()).thenReturn(currentUser);

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
    }

    @Test
    void vaultEncryption_incrementsCounter() {
        AiKeyVaultService vaultService = vaultService();
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(i -> i.getArgument(0));

        vaultService.saveKey(10, AiProvider.OPENAI, "sk-test");

        assertEquals(1.0, meterRegistry.counter("ai.vault.encryptions.total").count());
    }

    @Test
    void vaultDecryption_incrementsCounter() {
        AiKeyVaultService vaultService = vaultService();
        java.util.concurrent.atomic.AtomicReference<UserApiKey> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(i -> {
            UserApiKey value = i.getArgument(0);
            saved.set(value);
            return value;
        });

        vaultService.saveKey(10, AiProvider.OPENAI, "sk-test");
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI))
                .thenReturn(Optional.of(saved.get()));
        vaultService.getDecryptedKey(10, AiProvider.OPENAI);

        assertEquals(1.0, meterRegistry.counter("ai.vault.decryptions.total").count());
    }

    @Test
    void vaultCrypto_recordsDuration() {
        AiKeyVaultService vaultService = vaultService();
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(i -> i.getArgument(0));

        vaultService.saveKey(10, AiProvider.OPENAI, "sk-test");

        assertNotNull(meterRegistry.find("ai.vault.crypto.duration").timer());
    }

    @Test
    void chatMessage_incrementsCounterWithTags() {
        AiChatService chatService = chatService(nestBridgeSuccess(), vaultServiceWithDecryptedKey());

        chatService.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        assertEquals(1.0, meterRegistry.counter("ai.chat.messages.total", "role", "USER", "provider", "OPENAI").count());
    }

    @Test
    void chatStream_recordsDuration() {
        AiChatService chatService = chatService(nestBridgeSuccess(), vaultServiceWithDecryptedKey());

        chatService.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        waitUntil(() -> {
            var timer = meterRegistry.find("ai.chat.stream.duration").tag("provider", "OPENAI").timer();
            return timer != null && timer.count() > 0;
        }, 2000);

        assertNotNull(meterRegistry.find("ai.chat.stream.duration").tag("provider", "OPENAI").timer());
    }

    @Test
    void chatError_incrementsCounterWithType() {
        AiChatService chatService = chatService(nestBridgeSuccess(), vaultServiceMissingKey());

        try {
            chatService.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");
        } catch (Exception ignored) {
            // expected
        }

        assertEquals(1.0, meterRegistry.counter("ai.chat.errors.total", "type", "no_key").count());
    }

    @Test
    void smgCompression_recordsDuration() {
        SemanticMemoryGraphService smgService = semanticMemoryService(new EntityMemory());

        smgService.compress(List.of(message(MessageRole.USER, "hola")), "es");

        assertNotNull(meterRegistry.find("ai.smg.compression.duration").timer());
    }

    @Test
    void smgEntities_incrementsCounter() {
        EntityMemory memory = new EntityMemory();
        memory.addProductById(42);
        SemanticMemoryGraphService smgService = semanticMemoryService(memory);

        smgService.compress(List.of(message(MessageRole.USER, "stock tomate")), "es");

        assertEquals(1.0, meterRegistry.counter("ai.smg.entities.extracted.total").count());
    }

    @Test
    void rateLimitRejected_incrementsCounter() {
        AiRateLimitService rateLimitService = new AiRateLimitService(
                rateLimitProperties,
                stringRedisTemplate,
                circuitBreakerRegistry,
                aiChatRepository,
                aiChatMessageRepository,
                meterRegistry
        );
        when(aiChatRepository.countByUserIdAndStatus(10, AiChatStatus.ACTIVE)).thenReturn(5L);

        boolean allowed = rateLimitService.canCreateChat(10);

        assertTrue(!allowed);
        assertEquals(1.0, meterRegistry.counter("ai.ratelimit.rejected.total", "reason", "max_chats").count());
    }

    @Test
    void nestStreamTokens_incrementsCounter() {
        when(circuitBreakerRegistry.circuitBreaker("nest")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(nestRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.accept(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(NestCompletionRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.exchange(any())).thenReturn(new NestStreamBridgeService.StreamCompletionResult("ok", 3, 7));

        NestStreamBridgeService bridge = new NestStreamBridgeService(
                nestRestClient,
                nestProperties(),
                circuitBreakerRegistry,
                meterRegistry,
                new ObjectMapper(),
                Optional.of(auditEventProducer)
        );

        bridge.streamCompletion(
                new NestCompletionRequest("ctx", "sk-test", "OPENAI", "Admin", "es", "gpt-4o"),
                new SseEmitter(5000L),
                "jwt"
        );

        assertEquals(10.0, meterRegistry.counter("ai.nest.stream.tokens.total").count());
    }

    private AiKeyVaultService vaultService() {
        return new AiKeyVaultService(
                vaultProperties,
                providerProperties,
                userApiKeyRepository,
                rateLimitProperties,
                meterRegistry,
                Optional.of(auditEventProducer)
        );
    }

    private AiKeyVaultService vaultServiceWithDecryptedKey() {
        AiKeyVaultService vaultService = org.mockito.Mockito.mock(AiKeyVaultService.class);
        when(vaultService.getDecryptedKey(10, AiProvider.OPENAI)).thenReturn("sk-test");
        return vaultService;
    }

    private AiKeyVaultService vaultServiceMissingKey() {
        AiKeyVaultService vaultService = org.mockito.Mockito.mock(AiKeyVaultService.class);
        when(vaultService.getDecryptedKey(10, AiProvider.OPENAI)).thenThrow(new ResourceNotFoundException("missing"));
        return vaultService;
    }

    private NestStreamBridgeService nestBridgeSuccess() {
        NestStreamBridgeService bridge = org.mockito.Mockito.mock(NestStreamBridgeService.class);
        when(bridge.streamCompletion(any(NestCompletionRequest.class), any(SseEmitter.class), any(String.class)))
                .thenReturn(new NestStreamBridgeService.StreamCompletionResult("respuesta", 2, 8));
        return bridge;
    }

    private AiChatService chatService(NestStreamBridgeService bridge, AiKeyVaultService vaultService) {
        AiRateLimitService rateLimitService = new AiRateLimitService(
                rateLimitProperties,
                stringRedisTemplate,
                circuitBreakerRegistry,
                aiChatRepository,
                aiChatMessageRepository,
                meterRegistry
        );
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat()));
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(i -> i.getArgument(0));
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(i -> {
            AiChatMessage msg = i.getArgument(0);
            if (msg.getId() == null) {
                msg.setId(msg.getRole() == MessageRole.USER ? 1L : 2L);
            }
            return msg;
        });
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message(MessageRole.USER, "hola")));
        when(zSetOperations.zCard(any(String.class))).thenReturn(0L);
        when(zSetOperations.removeRangeByScore(any(String.class), any(Double.class), any(Double.class))).thenReturn(0L);
        when(zSetOperations.add(any(String.class), any(String.class), any(Double.class))).thenReturn(true);
        when(stringRedisTemplate.expire(any(String.class), eq(2L), eq(TimeUnit.MINUTES))).thenReturn(true);

        return new AiChatService(
                aiChatRepository,
                aiChatMessageRepository,
                vaultService,
                semanticMemoryService(new EntityMemory()),
                bridge,
                rateLimitService,
                securityContextHelper,
                chatProperties(),
                providerProperties,
                nestProperties(),
                meterRegistry,
                Optional.of(auditEventProducer)
        );
    }

    private SemanticMemoryGraphService semanticMemoryService(EntityMemory extractedMemory) {
        AiSmgProperties props = new AiSmgProperties();
        props.setTokenBudget(1200);
        props.setMaxWorkingMemoryMessages(10);

        EntityExtractor extractor = org.mockito.Mockito.mock(EntityExtractor.class);
        EntityEnricher enricher = org.mockito.Mockito.mock(EntityEnricher.class);
        TopicClusterer topicClusterer = new TopicClusterer(props);
        IntentDetector intentDetector = new IntentDetector(new AiIntentProperties());
        McpUtilityService mcpUtilityService = org.mockito.Mockito.mock(McpUtilityService.class);

        when(extractor.extract(any())).thenReturn(extractedMemory);
        when(mcpUtilityService.getSystemContext()).thenReturn(McpSystemContextDto.builder().totalProducts(10).build());

        return new SemanticMemoryGraphService(
                new TokenEstimator(props),
                extractor,
                enricher,
                topicClusterer,
                intentDetector,
                new DecayFunction(props),
                new ToolResultCompressor(props, new ObjectMapper()),
                props,
                mcpUtilityService,
                meterRegistry
        );
    }

    private AiChatProperties chatProperties() {
        AiChatProperties props = new AiChatProperties();
        props.setDefaultProvider("OPENAI");
        props.setDefaultLanguage("es");
        props.setSupportedLanguages(List.of("es", "en", "fr"));
        props.setAutoArchiveOnLimit(true);
        return props;
    }

    private AiNestProperties nestProperties() {
        AiNestProperties props = new AiNestProperties();
        props.setCompletionEndpoint("/api/completion");
        props.setBaseUrl("http://localhost:9999");
        props.setServiceKey("test-service-key");
        props.setStreamTimeoutMs(120000L);
        return props;
    }

    private AiChat chat() {
        AiChat chat = new AiChat();
        chat.setId(100L);
        chat.setUser(currentUser);
        chat.setStatus(AiChatStatus.ACTIVE);
        chat.setActiveProvider(AiProvider.OPENAI);
        chat.setUserLanguage("es");
        chat.setMessageCount(0);
        chat.setTotalTokensConsumed(0);
        chat.setCreatedAt(LocalDateTime.now());
        chat.setLastMessageAt(LocalDateTime.now());
        return chat;
    }

    private AiChatMessage message(MessageRole role, String content) {
        return AiChatMessage.builder()
                .role(role)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void waitUntil(BooleanSupplier condition, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
