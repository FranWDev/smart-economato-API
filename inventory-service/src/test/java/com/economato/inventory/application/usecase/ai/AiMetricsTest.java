package com.economato.inventory.application.usecase.ai;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpSystemContextDto;
import com.economato.inventory.application.dto.shared.mcp.NestCompletionRequest;
import com.economato.inventory.application.dto.shared.mcp.ToolCallInfo;
import com.economato.inventory.application.usecase.mcp.mcp.McpUtilityService;
import com.economato.inventory.application.usecase.shared.NestStreamBridgeService;
import com.economato.inventory.application.usecase.smg.model.shared.CompressedContext;
import com.economato.inventory.application.usecase.smg.model.shared.EntityMemory;
import com.economato.inventory.application.usecase.smg.shared.DecayFunction;
import com.economato.inventory.application.usecase.smg.shared.EntityEnricher;
import com.economato.inventory.application.usecase.smg.shared.EntityExtractor;
import com.economato.inventory.application.usecase.smg.shared.IntentDetector;
import com.economato.inventory.application.usecase.smg.shared.SemanticMemoryGraphService;
import com.economato.inventory.application.usecase.smg.shared.ToolResultCompressor;
import com.economato.inventory.application.usecase.smg.shared.TopicClusterer;
import com.economato.inventory.application.usecase.smg.user.TokenEstimator;
import com.economato.inventory.domain.model.ai.AiChat;
import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.ai.AiChatStatus;
import com.economato.inventory.domain.model.ai.AiProvider;
import com.economato.inventory.domain.model.user.MessageRole;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.user.UserApiKey;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.GlobalApiKeyRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserApiKeyRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiChatProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiIntentProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiNestProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiRateLimitProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiSmgProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiVaultProperties;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unused")
class AiMetricsTest {

    @Mock private UserApiKeyRepository userApiKeyRepository;
    @Mock private GlobalApiKeyRepository globalApiKeyRepository;
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
    @Mock private I18nService i18nService;

    private SimpleMeterRegistry meterRegistry;
    private AiKeyVaultService aiKeyVaultService;
    private AiRateLimitService aiRateLimitService;
    private SemanticMemoryGraphService semanticMemoryGraphService;
    private User currentUser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        AiProviderProperties providerProperties = new AiProviderProperties();
        AiProviderProperties.ProviderConfig openAiCfg = new AiProviderProperties.ProviderConfig();
        openAiCfg.setEnabled(true);
        openAiCfg.setModelDefault("gpt-4o");
        openAiCfg.setKeyPrefix("sk-");
        providerProperties.setConfigs(Map.of("OPENAI", openAiCfg));

        AiVaultProperties vaultProperties = new AiVaultProperties();
        vaultProperties.setMasterKey("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        vaultProperties.setCurrentKeyVersion(1);

        AiRateLimitProperties rateLimitProperties = new AiRateLimitProperties();
        rateLimitProperties.setMaxApiKeysPerUser(5);
        rateLimitProperties.setMessagesPerMinute(10);
        rateLimitProperties.setMaxChatsPerUser(5);
        rateLimitProperties.setMaxMessagesPerChat(50);
        rateLimitProperties.setFailOpen(true);

        aiKeyVaultService = new AiKeyVaultService(
                vaultProperties,
                providerProperties,
                userApiKeyRepository,
                globalApiKeyRepository,
                rateLimitProperties,
                meterRegistry,
                Optional.of(auditEventProducer),
                i18nService,
                mock(SecurityContextHelper.class)
        );

        aiRateLimitService = new AiRateLimitService(
                rateLimitProperties,
                stringRedisTemplate,
                circuitBreakerRegistry,
                aiChatRepository,
                aiChatMessageRepository,
                meterRegistry
        );

        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        currentUser = new User();
        currentUser.setId(10);
        currentUser.setName("Admin");
        when(securityContextHelper.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void vaultCrypto_recordsCountersAndTimer() {
        AtomicReference<UserApiKey> saved = new AtomicReference<>();
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            saved.set(key);
            return key;
        });

        aiKeyVaultService.saveKey(10, AiProvider.OPENAI, "sk-test");
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI)).thenReturn(Optional.of(saved.get()));
        aiKeyVaultService.getDecryptedKey(10, AiProvider.OPENAI);

        assertEquals(1.0, meterRegistry.counter("ai.vault.encryptions.total").count());
        assertEquals(1.0, meterRegistry.counter("ai.vault.decryptions.total").count());
        assertNotNull(meterRegistry.find("ai.vault.crypto.duration").timer());
    }

    //@Test
    void chatMetrics_recordMessageTokensAndStreamDuration() {
        AiChat chat = chat();
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage msg = invocation.getArgument(0);
            msg.setId(msg.getRole() == MessageRole.USER ? 1L : 2L);
            return msg;
        });
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message(MessageRole.USER, "hola")));
        when(aiKeyVaultService.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(semanticMemoryGraphService.compress(any(), eq("es"))).thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 12, 0.7, "es"));
        when(nestRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(ArgumentMatchers.<URI>any())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.accept(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(NestCompletionRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.exchange(any())).thenReturn(new NestStreamBridgeService.StreamCompletionResult("respuesta", 11, 7, null, new ArrayList<ToolCallInfo>()));

        AiChatService service = new AiChatService(
                aiChatRepository,
                aiChatMessageRepository,
                aiKeyVaultService,
                semanticMemoryGraphService(),
                nestBridgeFromRestClient(),
                aiRateLimitServiceMock(),
                securityContextHelper,
                defaultChatProperties(),
                defaultProviderProperties(),
                defaultNestProperties(),
                meterRegistry,
                Optional.empty(),
                i18nService
        );

        service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        assertEquals(2.0, meterRegistry.counter("ai.chat.messages.total", "role", "USER", "provider", "OPENAI").count());
        assertEquals(1.0, meterRegistry.counter("ai.chat.tokens.total", "direction", "input", "provider", "OPENAI").count());
        assertEquals(7.0, meterRegistry.counter("ai.chat.tokens.total", "direction", "output", "provider", "OPENAI").count());
        assertNotNull(meterRegistry.find("ai.chat.stream.duration").timer());
    }

    //@Test
    void chatError_incrementsErrorCounter() {
        AiChat chat = chat();
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultService.getDecryptedKey(AiProvider.OPENAI)).thenThrow(new ResourceNotFoundException("missing"));

        AiChatService service = new AiChatService(
                aiChatRepository,
                aiChatMessageRepository,
                aiKeyVaultService,
                semanticMemoryGraphService(),
                nestBridgeMock(),
                aiRateLimitServiceMock(),
                securityContextHelper,
                defaultChatProperties(),
                defaultProviderProperties(),
                defaultNestProperties(),
                meterRegistry,
                Optional.empty(),
                i18nService
        );

        service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        assertEquals(1.0, meterRegistry.counter("ai.chat.errors.total", "type", "no_key").count());
    }

    @Test
    void smgCompression_recordsDurationAndEntityCounter() {
        EntityExtractor extractor = Mockito.mock(EntityExtractor.class);
        EntityEnricher enricher = Mockito.mock(EntityEnricher.class);
        McpUtilityService mcpUtilityService = Mockito.mock(McpUtilityService.class);
        when(extractor.extract(any())).thenReturn(new EntityMemory());
        when(mcpUtilityService.getSystemContext()).thenReturn(McpSystemContextDto.builder()
            .totalProducts(10)
            .pendingOrdersCount(2)
            .totalRecipes(5)
            .activeAlertsCount(1)
            .build());
        AiSmgProperties props = smgProperties();
        SemanticMemoryGraphService service = new SemanticMemoryGraphService(
                new TokenEstimator(props),
                extractor,
                enricher,
                new TopicClusterer(props),
                new IntentDetector(new AiIntentProperties()),
                new DecayFunction(props),
                new ToolResultCompressor(props, new ObjectMapper()),
                props,
                mcpUtilityService,
                meterRegistry
        );

        service.compress(List.of(message(MessageRole.USER, "hola")), "es");

        assertNotNull(meterRegistry.find("ai.smg.compression.duration").timer());
    }

    //@Test
    void rateLimitRejected_incrementsCounter() {
        when(aiChatRepository.countByUserIdAndStatus(10, AiChatStatus.ACTIVE)).thenReturn(5L);
        aiRateLimitService.isAllowed(10);
        assertEquals(1.0, meterRegistry.counter("ai.ratelimit.rejected.total", "reason", "max_chats").count());
    }

    //@Test
    void nestStreamTokens_incrementsCounter() {
        when(circuitBreakerRegistry.circuitBreaker("nest")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(nestRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(ArgumentMatchers.<URI>any())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.accept(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(NestCompletionRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.exchange(any())).thenReturn(new NestStreamBridgeService.StreamCompletionResult("respuesta", 3, 4, null, new ArrayList<ToolCallInfo>()));

        NestStreamBridgeService service = nestBridgeFromRestClient();
        service.streamCompletion(new NestCompletionRequest("ctx", "sk", "OPENAI", "Admin", "es", "gpt-4o"), new SseEmitter(1000L), "jwt");

        assertEquals(7.0, meterRegistry.counter("ai.nest.stream.tokens.total").count());
        assertNotNull(meterRegistry.find("ai.nest.stream.duration").timer());
    }

    private AiChatProperties defaultChatProperties() {
        AiChatProperties props = new AiChatProperties();
        props.setDefaultProvider("OPENAI");
        props.setDefaultLanguage("es");
        props.setSupportedLanguages(List.of("es", "en"));
        props.setAutoArchiveOnLimit(true);
        return props;
    }

    private AiProviderProperties defaultProviderProperties() {
        AiProviderProperties providerProperties = new AiProviderProperties();
        AiProviderProperties.ProviderConfig openAiCfg = new AiProviderProperties.ProviderConfig();
        openAiCfg.setEnabled(true);
        openAiCfg.setModelDefault("gpt-4o");
        openAiCfg.setKeyPrefix("sk-");
        providerProperties.setConfigs(Map.of("OPENAI", openAiCfg));
        return providerProperties;
    }

    private AiNestProperties defaultNestProperties() {
        AiNestProperties nestProperties = new AiNestProperties();
        nestProperties.setBaseUrl("http://localhost:9999");
        nestProperties.setServiceKey("test-service-key");
        nestProperties.setCompletionEndpoint("/api/completion");
        nestProperties.setStreamTimeoutMs(120000L);
        return nestProperties;
    }

    private AiRateLimitService aiRateLimitServiceMock() {
        AiRateLimitProperties props = new AiRateLimitProperties();
        props.setMaxApiKeysPerUser(5);
        props.setMessagesPerMinute(10);
        props.setMaxChatsPerUser(5);
        props.setMaxMessagesPerChat(50);
        props.setFailOpen(true);
        return new AiRateLimitService(props, stringRedisTemplate, circuitBreakerRegistry, aiChatRepository, aiChatMessageRepository, meterRegistry);
    }

    private SemanticMemoryGraphService semanticMemoryGraphService() {
        AiSmgProperties props = smgProperties();
        return new SemanticMemoryGraphService(
                new TokenEstimator(props),
                Mockito.mock(EntityExtractor.class),
                Mockito.mock(EntityEnricher.class),
                new TopicClusterer(props),
                new IntentDetector(new AiIntentProperties()),
                new DecayFunction(props),
                new ToolResultCompressor(props, new ObjectMapper()),
                props,
                Mockito.mock(McpUtilityService.class),
                meterRegistry
        );
    }

    private AiSmgProperties smgProperties() {
        AiSmgProperties props = new AiSmgProperties();
        props.setTokenBudget(200);
        props.setWorkingMemoryWeight(0.5);
        props.setEntityMemoryWeight(0.2);
        props.setTopicMemoryWeight(0.1);
        props.setIntentMemoryWeight(0.1);
        props.setSystemContextWeight(0.1);
        props.setDecayLambda(1.5);
        props.setDecayFullThreshold(0.7);
        props.setDecayOnelinerThreshold(0.3);
        props.setMaxWorkingMemoryMessages(5);
        props.setToolResultMaxChars(120);
        props.setTokenEstimationDivisor(4);
        return props;
    }

    private NestStreamBridgeService nestBridgeMock() {
        return Mockito.mock(NestStreamBridgeService.class);
    }

    private NestStreamBridgeService nestBridgeFromRestClient() {
        AiNestProperties nestProperties = defaultNestProperties();
        return new NestStreamBridgeService(
                nestRestClient,
                nestProperties,
                circuitBreakerRegistry,
                meterRegistry,
                new ObjectMapper(),
                Optional.of(auditEventProducer),
                i18nService
        );
    }

    private AiChat chat() {
        AiChat chat = new AiChat();
        chat.setId(100L);
        chat.setUser(currentUser);
        chat.setStatus(AiChatStatus.ACTIVE);
        chat.setActiveProvider(AiProvider.OPENAI);
        chat.setUserLanguage("es");
        chat.setCreatedAt(LocalDateTime.now());
        chat.setLastMessageAt(LocalDateTime.now());
        return chat;
    }

    private AiChatMessage message(MessageRole role, String content) {
        return AiChatMessage.builder().role(role).content(content).createdAt(LocalDateTime.now()).build();
    }

}