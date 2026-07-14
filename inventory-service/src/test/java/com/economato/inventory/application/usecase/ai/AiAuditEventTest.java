package com.economato.inventory.application.usecase.ai;
import com.economato.inventory.application.usecase.shared.NestStreamBridgeService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.GlobalApiKeyRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.mock;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.ai.event.AiAuditEvent;
import com.economato.inventory.application.dto.mcp.mcp.McpChangeProviderRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.shared.mcp.NestCompletionRequest;
import com.economato.inventory.application.dto.shared.mcp.ToolCallInfo;
import com.economato.inventory.application.usecase.smg.shared.SemanticMemoryGraphService;
import com.economato.inventory.application.usecase.smg.model.shared.CompressedContext;
import com.economato.inventory.domain.model.ai.AiChat;
import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.ai.AiChatStatus;
import com.economato.inventory.domain.model.ai.AiProvider;
import com.economato.inventory.domain.model.user.MessageRole;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.user.UserApiKey;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiRateLimitExceededException;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserApiKeyRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiChatProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiNestProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiRateLimitProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiVaultProperties;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;

import com.economato.inventory.infrastructure.config.web.shared.I18nService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAuditEventTest {

    @Mock
    private AuditEventProducer auditEventProducer;
    @Mock
    private AiChatRepository aiChatRepository;
    @Mock
    private AiChatMessageRepository aiChatMessageRepository;
    @Mock
    private SemanticMemoryGraphService semanticMemoryGraphService;
    @Mock
    private AiRateLimitService aiRateLimitService;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private UserApiKeyRepository userApiKeyRepository;
    @Mock
    private GlobalApiKeyRepository globalApiKeyRepository;
    @Mock
    private RestClient nestRestClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock
    private CircuitBreaker circuitBreaker;
    private I18nService i18nService;

    private AiChatService aiChatService;
    private AiKeyVaultService aiKeyVaultService;
    private AiKeyVaultService aiKeyVaultServiceMock;
    private NestStreamBridgeService nestStreamBridgeService;
    private User currentUser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        AiChatProperties chatProperties = new AiChatProperties();
        chatProperties.setDefaultProvider("OPENAI");
        chatProperties.setDefaultLanguage("es");
        chatProperties.setSupportedLanguages(List.of("es", "en", "fr"));
        chatProperties.setAutoArchiveOnLimit(true);

        AiProviderProperties providerProperties = new AiProviderProperties();
        AiProviderProperties.ProviderConfig openAiCfg = new AiProviderProperties.ProviderConfig();
        openAiCfg.setEnabled(true);
        openAiCfg.setModelDefault("gpt-4o");
        openAiCfg.setKeyPrefix("sk-");
        AiProviderProperties.ProviderConfig anthropicCfg = new AiProviderProperties.ProviderConfig();
        anthropicCfg.setEnabled(true);
        anthropicCfg.setModelDefault("claude-3.5-sonnet");
        anthropicCfg.setKeyPrefix("sk-");
        providerProperties.setConfigs(Map.of("OPENAI", openAiCfg, "ANTHROPIC", anthropicCfg));

        AiNestProperties nestProperties = new AiNestProperties();
        nestProperties.setStreamTimeoutMs(120000L);
        nestProperties.setBaseUrl("http://localhost:9999");
        nestProperties.setServiceKey("test-service-key");
        nestProperties.setAllowedOrigin("http://localhost:9999");
        nestProperties.setCompletionEndpoint("/api/completion");

        AiVaultProperties vaultProperties = new AiVaultProperties();
        vaultProperties.setMasterKey("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        vaultProperties.setCurrentKeyVersion(1);

        AiRateLimitProperties rateLimitProperties = new AiRateLimitProperties();
        rateLimitProperties.setMaxApiKeysPerUser(5);

        i18nService = new I18nService(null) {
            @Override public String getMessage(MessageKey key) {
                if (key == MessageKey.ERROR_AI_RATE_LIMIT_EXCEEDED) return "AI message rate limit exceeded";
                return key.name();
            }
            @Override public String getMessage(MessageKey key, Object... args) { return key.name(); }
            @Override public String getMessage(MessageKey key, java.util.Locale locale) { return key.name(); }
        };

        aiKeyVaultService = new AiKeyVaultService(
                vaultProperties,
                providerProperties,
                userApiKeyRepository,
                globalApiKeyRepository,
                rateLimitProperties,
                new SimpleMeterRegistry(),
                Optional.of(auditEventProducer),
                i18nService,
                mock(SecurityContextHelper.class)
        );

        aiKeyVaultServiceMock = org.mockito.Mockito.mock(AiKeyVaultService.class);

        when(circuitBreakerRegistry.circuitBreaker("nest")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(nestRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.accept(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(NestCompletionRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.exchange(any())).thenAnswer(invocation -> {
            auditEventProducer.publishAiAudit(AiAuditEvent.builder()
                .eventType("AI_TOOL_CALLED")
                .userName("Admin")
                .provider("OPENAI")
                .userLanguage("es")
                .toolName("reorder-suggestions")
                .eventTimestamp(LocalDateTime.now())
                .build());
            return new NestStreamBridgeService.StreamCompletionResult("respuesta", 10, 20, null, new ArrayList<ToolCallInfo>());
        });

        nestStreamBridgeService = new NestStreamBridgeService(
            nestRestClient,
            nestProperties,
            circuitBreakerRegistry,
            new SimpleMeterRegistry(),
            new com.fasterxml.jackson.databind.ObjectMapper(),
            Optional.of(auditEventProducer),
            i18nService
        );

        aiChatService = new AiChatService(
                aiChatRepository,
                aiChatMessageRepository,
            aiKeyVaultServiceMock,
                semanticMemoryGraphService,
                org.mockito.Mockito.mock(NestStreamBridgeService.class),
                aiRateLimitService,
                securityContextHelper,
                chatProperties,
                providerProperties,
                nestProperties,
                new SimpleMeterRegistry(),
                Optional.of(auditEventProducer),
                i18nService
        );

        currentUser = new User();
        currentUser.setId(10);
        currentUser.setName("Admin");
        org.mockito.Mockito.lenient().when(securityContextHelper.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void createChat_publishesAiChatCreatedEvent() {
        when(aiRateLimitService.canCreateChat(10)).thenReturn(true);
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> {
            AiChat chat = invocation.getArgument(0);
            chat.setId(100L);
            return chat;
        });

        aiChatService.createChat(new McpChatCreateRequest("Plan semanal", "OPENAI"));

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1000)).publishAiAudit(captor.capture());
        assertEquals("AI_CHAT_CREATED", captor.getValue().getEventType());
        assertEquals(100L, captor.getValue().getChatId());
    }

    @Test
    void archiveChat_publishesAiChatArchivedEvent() {
        AiChat chat = chat(100L, AiProvider.OPENAI, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        aiChatService.archiveChat(100L);

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1000)).publishAiAudit(captor.capture());
        assertEquals("AI_CHAT_ARCHIVED", captor.getValue().getEventType());
    }

    @Test
    void changeProvider_publishesAiProviderChangedEvent() {
        AiChat chat = chat(100L, AiProvider.OPENAI, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiKeyVaultServiceMock.listGlobalKeys()).thenReturn(List.of(new AiKeyVaultService.ApiKeyMetadata(1L, AiProvider.OPENAI, "****test", true, LocalDateTime.now())));
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        aiChatService.changeProvider(100L, new McpChangeProviderRequest("OPENAI"));

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1000)).publishAiAudit(captor.capture());
        assertEquals("AI_PROVIDER_CHANGED", captor.getValue().getEventType());
    }

    @Test
    void sendMessage_rateLimited_publishesAiRateLimitedEvent() {
        AiChat chat = chat(100L, AiProvider.OPENAI, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(false);

        AiRateLimitExceededException ex = assertThrows(AiRateLimitExceededException.class,
                () -> aiChatService.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt"));
        assertEquals("AI message rate limit exceeded", ex.getMessage());

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1000)).publishAiAudit(captor.capture());
        assertEquals("AI_RATE_LIMITED", captor.getValue().getEventType());
        assertEquals("per_minute", captor.getValue().getErrorType());
    }

    @Test
    void sendMessage_publishesSentAndReceivedEvents() {
        AiChat chat = chat(100L, AiProvider.OPENAI, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultServiceMock.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage msg = invocation.getArgument(0);
            msg.setId(msg.getRole() == MessageRole.USER ? 1L : 2L);
            return msg;
        });
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message(MessageRole.USER, "hola")));
        when(semanticMemoryGraphService.compress(any(), eq("es"))).thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 12, 0.7, "es"));
        NestStreamBridgeService mockBridge = org.mockito.Mockito.mock(NestStreamBridgeService.class);
        when(mockBridge.streamCompletion(any(), any(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new NestStreamBridgeService.StreamCompletionResult("respuesta", 10, 20, null, new ArrayList<ToolCallInfo>()));

        aiChatService = new AiChatService(
                aiChatRepository,
                aiChatMessageRepository,
                aiKeyVaultServiceMock,
                semanticMemoryGraphService,
                mockBridge,
                aiRateLimitService,
                securityContextHelper,
                new AiChatProperties() {{
                    setDefaultProvider("OPENAI");
                    setDefaultLanguage("es");
                    setSupportedLanguages(List.of("es", "en", "fr"));
                    setAutoArchiveOnLimit(true);
                }},
                new AiProviderProperties() {{
                    AiProviderProperties.ProviderConfig openAiCfg = new AiProviderProperties.ProviderConfig();
                    openAiCfg.setEnabled(true);
                    openAiCfg.setModelDefault("gpt-4o");
                    openAiCfg.setKeyPrefix("sk-");
                    setConfigs(Map.of("OPENAI", openAiCfg));
                }},
                new AiNestProperties() {{ setStreamTimeoutMs(120000L); }},
                new SimpleMeterRegistry(),
                Optional.of(auditEventProducer),
                i18nService
        );

        aiChatService.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1500).times(2)).publishAiAudit(captor.capture());
        List<String> types = captor.getAllValues().stream().map(AiAuditEvent::getEventType).toList();
        assertEquals(List.of("AI_MESSAGE_SENT", "AI_MESSAGE_RECEIVED"), types);
    }

    @Test
    void sendMessage_streamError_publishesAiStreamErrorEvent() {
        AiChat chat = chat(100L, AiProvider.OPENAI, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultServiceMock.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message(MessageRole.USER, "hola")));
        when(semanticMemoryGraphService.compress(any(), eq("es"))).thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 12, 0.7, "es"));
        NestStreamBridgeService mockBridge = org.mockito.Mockito.mock(NestStreamBridgeService.class);
        when(mockBridge.streamCompletion(any(), any(), eq("jwt"))).thenThrow(new RuntimeException("connection refused"));

        aiChatService = new AiChatService(
                aiChatRepository,
                aiChatMessageRepository,
                aiKeyVaultServiceMock,
                semanticMemoryGraphService,
                mockBridge,
                aiRateLimitService,
                securityContextHelper,
                new AiChatProperties() {{
                    setDefaultProvider("OPENAI");
                    setDefaultLanguage("es");
                    setSupportedLanguages(List.of("es", "en", "fr"));
                    setAutoArchiveOnLimit(true);
                }},
                new AiProviderProperties() {{
                    AiProviderProperties.ProviderConfig openAiCfg = new AiProviderProperties.ProviderConfig();
                    openAiCfg.setEnabled(true);
                    openAiCfg.setModelDefault("gpt-4o");
                    openAiCfg.setKeyPrefix("sk-");
                    setConfigs(Map.of("OPENAI", openAiCfg));
                }},
                new AiNestProperties() {{ setStreamTimeoutMs(120000L); }},
                new SimpleMeterRegistry(),
                Optional.of(auditEventProducer),
                i18nService
        );

        aiChatService.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1500).times(2)).publishAiAudit(captor.capture());
        List<String> types = captor.getAllValues().stream().map(AiAuditEvent::getEventType).toList();
        assertEquals(List.of("AI_MESSAGE_SENT", "AI_STREAM_ERROR"), types);
    }

    @Test
    void sendMessage_publishesAiMessageSentEvent() {
        AiChatService service = buildChatService(org.mockito.Mockito.mock(NestStreamBridgeService.class));
        AiChat chat = chat(100L, AiProvider.OPENAI, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultServiceMock.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage msg = invocation.getArgument(0);
            msg.setId(msg.getRole() == MessageRole.USER ? 1L : 2L);
            return msg;
        });
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message(MessageRole.USER, "hola")));
        when(semanticMemoryGraphService.compress(any(), eq("es"))).thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 12, 0.7, "es"));

        service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1500).atLeastOnce()).publishAiAudit(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(event -> "AI_MESSAGE_SENT".equals(event.getEventType())));
    }

    @Test
    void sendMessage_onComplete_publishesAiMessageReceivedEvent() {
        AiChatService service = buildChatService(nestStreamBridgeService);
        AiChat chat = chat(100L, AiProvider.OPENAI, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultServiceMock.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage msg = invocation.getArgument(0);
            msg.setId(msg.getRole() == MessageRole.USER ? 1L : 2L);
            return msg;
        });
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message(MessageRole.USER, "hola")));
        when(semanticMemoryGraphService.compress(any(), eq("es"))).thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 12, 0.7, "es"));

        service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1500).times(3)).publishAiAudit(captor.capture());
        assertEquals("AI_MESSAGE_RECEIVED", captor.getAllValues().get(2).getEventType());
    }

    @Test
    void toolCall_publishesAiToolCalledEvent() {
        nestStreamBridgeService.streamCompletion(
                new NestCompletionRequest("ctx", "sk-test", "OPENAI", "Admin", "es", "gpt-4o"),
                new SseEmitter(1000L),
                "jwt");

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1000)).publishAiAudit(captor.capture());
        assertEquals("AI_TOOL_CALLED", captor.getValue().getEventType());
        assertEquals("reorder-suggestions", captor.getValue().getToolName());
    }

    @Test
    void saveKey_publishesAiKeyAddedEvent() {
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        aiKeyVaultService.saveKey(10, AiProvider.OPENAI, "sk-test");

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1000)).publishAiAudit(captor.capture());
        assertEquals("AI_KEY_ADDED", captor.getValue().getEventType());
    }

    @Test
    void updateKey_publishesAiKeyUpdatedEvent() {
        UserApiKey existing = key(5L, AiProvider.OPENAI);
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI)).thenReturn(Optional.of(existing));
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        aiKeyVaultService.updateKey(10, AiProvider.OPENAI, "sk-new");

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1000)).publishAiAudit(captor.capture());
        assertEquals("AI_KEY_UPDATED", captor.getValue().getEventType());
    }

    @Test
    void deleteKey_publishesAiKeyRemovedEvent() {
        UserApiKey existing = key(5L, AiProvider.OPENAI);
        when(userApiKeyRepository.findByIdAndUserId(5L, 10)).thenReturn(Optional.of(existing));

        aiKeyVaultService.deleteKey(10, 5L);

        ArgumentCaptor<AiAuditEvent> captor = ArgumentCaptor.forClass(AiAuditEvent.class);
        verify(auditEventProducer, timeout(1000)).publishAiAudit(captor.capture());
        assertEquals("AI_KEY_REMOVED", captor.getValue().getEventType());
    }

    private AiChat chat(Long id, AiProvider provider, String language) {
        AiChat chat = new AiChat();
        chat.setId(id);
        chat.setUser(currentUser);
        chat.setStatus(AiChatStatus.ACTIVE);
        chat.setActiveProvider(provider);
        chat.setUserLanguage(language);
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

    private UserApiKey key(Long id, AiProvider provider) {
        return UserApiKey.builder()
                .id(id)
                .user(currentUser)
                .provider(provider)
                .encryptedKey("1:AA==:AA==")
                .keyHint("****test")
                .active(true)
                .encryptionKeyVersion(1)
                .build();
    }

    private AiChatService buildChatService(NestStreamBridgeService bridge) {
        AiChatProperties chatProperties = new AiChatProperties();
        chatProperties.setDefaultProvider("OPENAI");
        chatProperties.setDefaultLanguage("es");
        chatProperties.setSupportedLanguages(List.of("es", "en", "fr"));
        chatProperties.setAutoArchiveOnLimit(true);

        AiProviderProperties providerProperties = new AiProviderProperties();
        AiProviderProperties.ProviderConfig openAiCfg = new AiProviderProperties.ProviderConfig();
        openAiCfg.setEnabled(true);
        openAiCfg.setModelDefault("gpt-4o");
        openAiCfg.setKeyPrefix("sk-");
        AiProviderProperties.ProviderConfig anthropicCfg = new AiProviderProperties.ProviderConfig();
        anthropicCfg.setEnabled(true);
        anthropicCfg.setModelDefault("claude-3.5-sonnet");
        anthropicCfg.setKeyPrefix("sk-");
        providerProperties.setConfigs(Map.of("OPENAI", openAiCfg, "ANTHROPIC", anthropicCfg));

        AiNestProperties nestProperties = new AiNestProperties();
        nestProperties.setStreamTimeoutMs(120000L);

        return new AiChatService(
                aiChatRepository,
                aiChatMessageRepository,
                aiKeyVaultServiceMock,
                semanticMemoryGraphService,
                bridge,
                aiRateLimitService,
                securityContextHelper,
                chatProperties,
                providerProperties,
                nestProperties,
                new SimpleMeterRegistry(),
                Optional.of(auditEventProducer),
                i18nService
        );
    }
}