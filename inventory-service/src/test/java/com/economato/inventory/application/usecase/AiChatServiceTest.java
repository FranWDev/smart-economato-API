package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.mcp.McpChangeProviderRequest;
import com.economato.inventory.application.dto.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.mcp.NestCompletionRequest;
import com.economato.inventory.application.usecase.smg.SemanticMemoryGraphService;
import com.economato.inventory.application.usecase.smg.model.CompressedContext;
import com.economato.inventory.domain.model.AiChat;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.AiChatStatus;
import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiChatLimitReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiKeyNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiMaxMessagesReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiProviderDisabledException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiRateLimitExceededException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AiChatRepository;
import com.economato.inventory.infrastructure.config.ai.AiChatProperties;
import com.economato.inventory.infrastructure.config.ai.AiNestProperties;
import com.economato.inventory.infrastructure.config.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

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

    private AiChatService service;
    private User currentUser;

    @BeforeEach
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
                Optional.empty()
        );

        currentUser = new User();
        currentUser.setId(10);
        currentUser.setName("Admin");
        when(securityContextHelper.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void createChat_validRequest_createsAndReturns() {
        when(aiRateLimitService.canCreateChat(10)).thenReturn(true);
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> {
            AiChat chat = invocation.getArgument(0);
            chat.setId(100L);
            return chat;
        });

        var response = service.createChat(new McpChatCreateRequest("Plan semanal", "OPENAI"));

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("OPENAI", response.activeProvider());
    }

    @Test
    void createChat_exceedsMaxChats_throwsException() {
        when(aiRateLimitService.canCreateChat(10)).thenReturn(false);

        assertThrows(AiChatLimitReachedException.class,
                () -> service.createChat(new McpChatCreateRequest("", "OPENAI")));
    }

    @Test
    void createChat_disabledProvider_throwsException() {
        when(aiRateLimitService.canCreateChat(10)).thenReturn(true);

        assertThrows(AiProviderDisabledException.class,
                () -> service.createChat(new McpChatCreateRequest("x", "MISTRAL")));
    }

    @Test
    void changeProvider_noKeyForNewProvider_throwsException() {
        AiChat chat = chat(100L);
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiKeyVaultService.listKeys(10)).thenReturn(List.of());

        assertThrows(AiKeyNotFoundException.class,
                () -> service.changeProvider(100L, new McpChangeProviderRequest("OPENAI")));
    }

    @Test
    void sendMessage_rateLimited_throwsException() {
        AiChat chat = chat(100L);
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(false);

        assertThrows(AiRateLimitExceededException.class,
                () -> service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt"));
    }

    @Test
    void sendMessage_maxMessagesReached_throwsOrArchives() {
        AiChat chat = chat(100L);
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(false);

        assertThrows(AiMaxMessagesReachedException.class,
                () -> service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt"));

        verify(aiChatRepository).save(any(AiChat.class));
    }

    @Test
    void sendMessage_callsNestAndPersistsAssistantMessage() {
        AiChat chat = chat(100L);
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);

        AiChatMessage userMessage = new AiChatMessage();
        userMessage.setId(1L);
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage msg = invocation.getArgument(0);
            if (msg.getRole() == MessageRole.USER) {
                msg.setId(1L);
                return msg;
            }
            if (msg.getRole() == MessageRole.ASSISTANT) {
                msg.setId(2L);
                return msg;
            }
            return msg;
        });

        when(aiKeyVaultService.getDecryptedKey(10, AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(userMessage));
        when(semanticMemoryGraphService.compress(any(), eq("es")))
                .thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 12, 0.7, "es"));
        when(nestStreamBridgeService.streamCompletion(any(NestCompletionRequest.class), any(), eq("jwt")))
                .thenReturn(new NestStreamBridgeService.StreamCompletionResult("respuesta", 10, 20));
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var emitter = service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");

        assertNotNull(emitter);
        verify(nestStreamBridgeService, timeout(1000)).streamCompletion(any(NestCompletionRequest.class), any(), eq("jwt"));
        verify(aiChatMessageRepository, timeout(1000).times(2)).save(any(AiChatMessage.class));
        verify(aiRateLimitService, timeout(1000)).recordRequest(10);
    }

    @Test
    void sendMessage_keyNotConfigured_throwsException() {
        AiChat chat = chat(100L);
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiKeyVaultService.getDecryptedKey(10, AiProvider.OPENAI)).thenThrow(new ResourceNotFoundException("missing"));

        assertThrows(AiKeyNotFoundException.class,
                () -> service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt"));
    }

    @Test
    void listChats_returnsOnlyOwnedActiveChats() {
        when(aiChatRepository.findByUserIdAndStatusOrderByLastMessageAtDesc(10, AiChatStatus.ACTIVE))
                .thenReturn(List.of(chat(100L)));

        var result = service.listChats();

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).id());
    }

    @Test
    void getChatHistory_nonExistentChat_throwsException() {
        when(aiChatRepository.findByIdAndUserId(anyLong(), anyInt())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getChatHistory(999L));
    }

    private AiChat chat(Long id) {
        AiChat chat = new AiChat();
        chat.setId(id);
        chat.setUser(currentUser);
        chat.setStatus(AiChatStatus.ACTIVE);
        chat.setActiveProvider(AiProvider.OPENAI);
        chat.setUserLanguage("es");
        chat.setCreatedAt(LocalDateTime.now());
        chat.setLastMessageAt(LocalDateTime.now());
        return chat;
    }
}
