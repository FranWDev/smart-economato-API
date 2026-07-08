package com.economato.inventory.application.usecase.ai;
import com.economato.inventory.application.usecase.shared.NestStreamBridgeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.application.dto.mcp.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.shared.mcp.ToolCallInfo;
import com.economato.inventory.application.dto.shared.mcp.NestCompletionRequest;
import com.economato.inventory.application.usecase.smg.shared.SemanticMemoryGraphService;
import com.economato.inventory.application.usecase.smg.model.shared.CompressedContext;
import com.economato.inventory.domain.model.ai.AiChat;
import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.ai.AiChatStatus;
import com.economato.inventory.domain.model.ai.AiProvider;
import com.economato.inventory.domain.model.user.MessageRole;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiConcurrentStreamException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiChatProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiNestProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;

import com.economato.inventory.infrastructure.config.web.shared.I18nService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class AiChatConcurrencyTest {

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
        chatProperties.setMaxConcurrentStreamsPerUser(1);

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
    void concurrentChatCreation_sameUser_respectsLimit() throws Exception {
        AtomicInteger allowedCalls = new AtomicInteger(0);
        AtomicInteger idSequence = new AtomicInteger(100);
        when(aiRateLimitService.canCreateChat(10)).thenAnswer(invocation -> allowedCalls.incrementAndGet() <= 2);
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> {
            AiChat chat = invocation.getArgument(0);
            chat.setId((long) idSequence.getAndIncrement());
            return chat;
        });

        int threadCount = 8;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    var result = service.createChat(new McpChatCreateRequest("Plan", "OPENAI"));
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(2, successCount.get());
        assertTrue(failureCount.get() > 0);
    }

    @Test
    void concurrentMessages_sameChat_messageCountConsistent() throws Exception {
        AiChat chat = baseChat(100L, AiProvider.OPENAI, "es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultService.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            message.setId(message.getRole() == MessageRole.USER ? 1L : 2L);
            return message;
        });
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());
        when(semanticMemoryGraphService.compress(any(), eq("es")))
                .thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 10, 0.7, "es"));
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AtomicReference<NestStreamBridgeService.StreamCompletionResult> resultRef = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger();
        when(nestStreamBridgeService.streamCompletion(any(NestCompletionRequest.class), any(), eq("jwt")))
                .thenAnswer(invocation -> {
                completions.incrementAndGet();
                    NestStreamBridgeService.StreamCompletionResult result = new NestStreamBridgeService.StreamCompletionResult("ok", 1, 1, null, new ArrayList<ToolCallInfo>());
                    resultRef.set(result);
                    return result;
                });

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Object lock = new Object();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    synchronized (lock) {
                        service.sendMessage(100L, new McpChatMessageRequest("mensaje " + index, "es"), "jwt");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        Thread.sleep(1500L);

        assertTrue(completions.get() > 0);
        assertNotNull(resultRef.get());
    }

    @Test
    void concurrentMessages_differentChats_noInterference() throws Exception {
        AiChat chat1 = baseChat(101L, AiProvider.OPENAI, "es");
        AiChat chat2 = baseChat(102L, AiProvider.OPENAI, "es");
        AiChat chat3 = baseChat(103L, AiProvider.OPENAI, "es");
        AiChat chat4 = baseChat(104L, AiProvider.OPENAI, "es");
        AiChat chat5 = baseChat(105L, AiProvider.OPENAI, "es");

        when(aiChatRepository.findByIdAndUserId(101L, 10)).thenReturn(Optional.of(chat1));
        when(aiChatRepository.findByIdAndUserId(102L, 10)).thenReturn(Optional.of(chat2));
        when(aiChatRepository.findByIdAndUserId(103L, 10)).thenReturn(Optional.of(chat3));
        when(aiChatRepository.findByIdAndUserId(104L, 10)).thenReturn(Optional.of(chat4));
        when(aiChatRepository.findByIdAndUserId(105L, 10)).thenReturn(Optional.of(chat5));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(any())).thenReturn(true);
        when(aiKeyVaultService.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            message.setId(message.getRole() == MessageRole.USER ? 1L : 2L);
            return message;
        });
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(semanticMemoryGraphService.compress(any(), eq("es")))
                .thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 10, 0.7, "es"));
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicInteger completions = new AtomicInteger();
        when(nestStreamBridgeService.streamCompletion(any(NestCompletionRequest.class), any(), eq("jwt")))
            .thenAnswer(invocation -> {
                completions.incrementAndGet();
                return new NestStreamBridgeService.StreamCompletionResult("ok", 1, 1, null, new ArrayList<ToolCallInfo>());
            });

        CountDownLatch latch = new CountDownLatch(5);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        Object lock = new Object();
        long[] chatIds = {101L, 102L, 103L, 104L, 105L};
        for (int i = 0; i < chatIds.length; i++) {
            final long chatId = chatIds[i];
            final int index = i;
            executor.submit(() -> {
                try {
                    synchronized (lock) {
                        service.sendMessage(chatId, new McpChatMessageRequest("mensaje " + index, "es"), "jwt");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        Thread.sleep(1500L);

        assertTrue(completions.get() > 0);
    }

    private AiChat baseChat(Long id, AiProvider provider, String language) {
        AiChat chat = new AiChat();
        chat.setId(id);
        chat.setUser(currentUser);
        chat.setStatus(AiChatStatus.ACTIVE);
        chat.setActiveProvider(provider);
        chat.setUserLanguage(language);
        return chat;
    }

    @Test
    void concurrentMessages_sameChat_processedSequentially() throws Exception {
        AiChat chat = new AiChat();
        chat.setId(100L);
        chat.setUser(currentUser);
        chat.setStatus(AiChatStatus.ACTIVE);
        chat.setActiveProvider(AiProvider.OPENAI);
        chat.setUserLanguage("es");
        when(aiChatRepository.findByIdAndUserId(100L, 10)).thenReturn(Optional.of(chat));
        when(aiRateLimitService.isAllowed(10)).thenReturn(true);
        when(aiRateLimitService.canSendMessage(100L)).thenReturn(true);
        when(aiKeyVaultService.getDecryptedKey(AiProvider.OPENAI)).thenReturn("sk-test");
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            message.setId(message.getRole() == MessageRole.USER ? 1L : 2L);
            return message;
        });
        when(aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());
        when(semanticMemoryGraphService.compress(any(), eq("es")))
                .thenReturn(new CompressedContext("sys", "intent", "entity", "topic", List.of(), 10, 0.7, "es"));

        CountDownLatch streamStarted = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        when(nestStreamBridgeService.streamCompletion(any(NestCompletionRequest.class), any(), eq("jwt")))
                .thenAnswer(invocation -> {
                    streamStarted.countDown();
                    releaseStream.await(5, TimeUnit.SECONDS);
                    return new NestStreamBridgeService.StreamCompletionResult("ok", 1, 1, null, new ArrayList<ToolCallInfo>());
                });


        var firstEmitter = service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");
        assertNotNull(firstEmitter);
        assertTrue(streamStarted.await(5, TimeUnit.SECONDS));

        AiConcurrentStreamException ex = assertThrows(AiConcurrentStreamException.class,
            () -> service.sendMessage(100L, new McpChatMessageRequest("hola 2", "es"), "jwt"));
        assertNotNull(ex);

        releaseStream.countDown();
        verify(nestStreamBridgeService, timeout(1000)).streamCompletion(any(NestCompletionRequest.class), any(), eq("jwt"));
    }
}