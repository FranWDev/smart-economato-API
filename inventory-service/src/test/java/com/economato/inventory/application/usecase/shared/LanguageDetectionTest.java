package com.economato.inventory.application.usecase.shared;
import com.economato.inventory.application.usecase.ai.AiChatService;
import com.economato.inventory.application.usecase.ai.AiKeyVaultService;
import com.economato.inventory.application.usecase.ai.AiRateLimitService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiChatProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiNestProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;

import com.economato.inventory.infrastructure.config.web.shared.I18nService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class LanguageDetectionTest {

    @Mock
    private AiChatRepository aiChatRepository;
    @Mock
    private AiChatMessageRepository aiChatMessageRepository;
    @Mock
    private AiKeyVaultService aiKeyVaultService;
    @Mock
    private SemanticMemoryGraphService semanticMemoryGraphService;
    @Mock
    private NestStreamBridgeService nestStreamBridgeService;
    @Mock
    private AiRateLimitService aiRateLimitService;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private I18nService i18nService;

    private AiChatService service;
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
        providerProperties.setConfigs(Map.of("OPENAI", openAiCfg));

        AiNestProperties nestProperties = new AiNestProperties();
        nestProperties.setStreamTimeoutMs(120000L);

        service = new AiChatService(
                aiChatRepository,
                aiChatMessageRepository,
                aiKeyVaultService,
                semanticMemoryGraphService,
                nestStreamBridgeService,
                aiRateLimitService,
                securityContextHelper,
                chatProperties,
                providerProperties,
                nestProperties,
                new SimpleMeterRegistry(),
                Optional.empty(),
                i18nService
        );

        currentUser = new User();
        currentUser.setId(10);
        currentUser.setName("Admin");
        when(securityContextHelper.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void createChat_withoutLanguage_usesDefault() {
        when(aiRateLimitService.canCreateChat(10)).thenReturn(true);
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> {
            AiChat chat = invocation.getArgument(0);
            chat.setId(100L);
            return chat;
        });

        var response = service.createChat(new McpChatCreateRequest("Plan semanal", "OPENAI"));

        assertEquals("es", response.userLanguage());
    }

    @Test
    void sendMessage_withLanguage_updatesChatAndForwardsToNest() {
        AiChat chat = chat(100L, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultService.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message(MessageRole.USER, "hola")));
        when(semanticMemoryGraphService.compress(any(), eq("en"))).thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 12, 0.7, "en"));
        when(nestStreamBridgeService.streamCompletion(any(), any(), eq("jwt")))
                .thenReturn(new NestStreamBridgeService.StreamCompletionResult("respuesta", 10, 20, null, new ArrayList<ToolCallInfo>()));
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SseEmitter emitter = service.sendMessage(100L, new McpChatMessageRequest("hola", "en"), "jwt");

        assertNotNull(emitter);
        ArgumentCaptor<NestCompletionRequest> requestCaptor = ArgumentCaptor.forClass(NestCompletionRequest.class);
        org.mockito.Mockito.verify(nestStreamBridgeService, timeout(1500)).streamCompletion(requestCaptor.capture(), any(), eq("jwt"));
        assertEquals("en", requestCaptor.getValue().userLanguage());
    }

    @Test
    void sendMessage_unsupportedLanguage_fallsBackToDefault() {
        AiChat chat = chat(100L, "fr");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultService.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(message(MessageRole.USER, "hola")));
        when(semanticMemoryGraphService.compress(any(), eq("es"))).thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 12, 0.7, "es"));
        when(nestStreamBridgeService.streamCompletion(any(), any(), eq("jwt")))
                .thenReturn(new NestStreamBridgeService.StreamCompletionResult("respuesta", 10, 20, null, new ArrayList<ToolCallInfo>())); 

        service.sendMessage(100L, new McpChatMessageRequest("hola", "xx"), "jwt");

        ArgumentCaptor<NestCompletionRequest> requestCaptor = ArgumentCaptor.forClass(NestCompletionRequest.class);
        org.mockito.Mockito.verify(nestStreamBridgeService, timeout(1500)).streamCompletion(requestCaptor.capture(), any(), eq("jwt"));
        assertEquals("es", requestCaptor.getValue().userLanguage());
    }

    private AiChat chat(Long id, String language) {
        AiChat chat = new AiChat();
        chat.setId(id);
        chat.setUser(currentUser);
        chat.setStatus(AiChatStatus.ACTIVE);
        chat.setActiveProvider(AiProvider.OPENAI);
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
}