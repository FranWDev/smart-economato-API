package com.economato.inventory.application.usecase;

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
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiConcurrentStreamException;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private AiChatService service;
    private User currentUser;

    @BeforeEach
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
                Optional.empty()
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
        when(aiKeyVaultService.getDecryptedKey(10, AiProvider.OPENAI)).thenReturn("sk-test");
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
                    return new NestStreamBridgeService.StreamCompletionResult("ok", 1, 1);
                });
        when(aiChatRepository.save(any(AiChat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var firstEmitter = service.sendMessage(100L, new McpChatMessageRequest("hola", "es"), "jwt");
        assertNotNull(firstEmitter);
        assertTrue(streamStarted.await(5, TimeUnit.SECONDS));

        assertThrows(AiConcurrentStreamException.class,
                () -> service.sendMessage(100L, new McpChatMessageRequest("hola 2", "es"), "jwt"));

        releaseStream.countDown();
        verify(nestStreamBridgeService, timeout(1000)).streamCompletion(any(NestCompletionRequest.class), any(), eq("jwt"));
    }
}