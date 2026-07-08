package com.economato.inventory.application.usecase.ai;
import com.economato.inventory.application.usecase.shared.NestStreamBridgeService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.ai.event.AiAuditEvent;
import com.economato.inventory.application.dto.user.mcp.McpApiKeyRequest;
import com.economato.inventory.application.dto.user.mcp.McpApiKeyResponseDto;
import com.economato.inventory.application.dto.mcp.mcp.McpChangeProviderRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageResponseDto;
import com.economato.inventory.application.dto.mcp.mcp.McpChatResponseDto;
import com.economato.inventory.application.dto.mcp.mcp.McpChatUpdateRequest;
import com.economato.inventory.application.dto.shared.mcp.NestCompletionRequest;
import com.economato.inventory.application.dto.shared.mcp.ToolCallInfo;
import com.economato.inventory.application.usecase.smg.shared.SemanticMemoryGraphService;
import com.economato.inventory.application.usecase.smg.model.shared.CompressedContext;
import com.economato.inventory.application.usecase.mcp.mcp.McpUtilityService;
import com.economato.inventory.domain.model.ai.AiChat;
import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.ai.AiChatStatus;
import com.economato.inventory.domain.model.ai.AiProvider;
import com.economato.inventory.domain.model.user.MessageRole;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiChatLimitReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiChatNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiConcurrentStreamException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiKeyNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiMaxMessagesReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiProviderDisabledException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiRateLimitExceededException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiStreamException;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiChatProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiNestProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.shared.security.JwtUtils;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class AiChatService {

    private final AiChatRepository aiChatRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiKeyVaultService aiKeyVaultService;
    private final PlatformTransactionManager transactionManager;
    private final SemanticMemoryGraphService semanticMemoryGraphService;
    private final McpUtilityService mcpUtilityService;
    private final NestStreamBridgeService nestStreamBridgeService;
    private final AiRateLimitService aiRateLimitService;
    private final SecurityContextHelper securityContextHelper;
    private final AiChatProperties aiChatProperties;
    private final AiProviderProperties aiProviderProperties;
    private final AiNestProperties aiNestProperties;
    private final MeterRegistry meterRegistry;
    private final Optional<AuditEventProducer> auditEventProducer;
    private final I18nService i18nService;

    private final ConcurrentHashMap<Integer, AtomicInteger> activeStreamsByUser = new ConcurrentHashMap<>();
    private final AtomicInteger activeStreamsGlobal = new AtomicInteger(0);
    private final JwtUtils jwtUtils;

    @Autowired
    public AiChatService(AiChatRepository aiChatRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AiKeyVaultService aiKeyVaultService,
            @Autowired(required = false) PlatformTransactionManager transactionManager,
            SemanticMemoryGraphService semanticMemoryGraphService,
            McpUtilityService mcpUtilityService,
            NestStreamBridgeService nestStreamBridgeService,
            AiRateLimitService aiRateLimitService,
            SecurityContextHelper securityContextHelper,
            AiChatProperties aiChatProperties,
            AiProviderProperties aiProviderProperties,
            AiNestProperties aiNestProperties,
            MeterRegistry meterRegistry,
            Optional<AuditEventProducer> auditEventProducer,
            I18nService i18nService,
            JwtUtils jwtUtils) {
        this.aiChatRepository = aiChatRepository;
        this.aiChatMessageRepository = aiChatMessageRepository;
        this.aiKeyVaultService = aiKeyVaultService;
        this.transactionManager = transactionManager;
        this.semanticMemoryGraphService = semanticMemoryGraphService;
        this.mcpUtilityService = mcpUtilityService;
        this.nestStreamBridgeService = nestStreamBridgeService;
        this.aiRateLimitService = aiRateLimitService;
        this.securityContextHelper = securityContextHelper;
        this.aiChatProperties = aiChatProperties;
        this.aiProviderProperties = aiProviderProperties;
        this.aiNestProperties = aiNestProperties;
        this.meterRegistry = meterRegistry;
        this.auditEventProducer = auditEventProducer;
        this.i18nService = i18nService;
        this.jwtUtils = jwtUtils;
    }

    public AiChatService(AiChatRepository aiChatRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AiKeyVaultService aiKeyVaultService,
            @Autowired(required = false) PlatformTransactionManager transactionManager,
            SemanticMemoryGraphService semanticMemoryGraphService,
            NestStreamBridgeService nestStreamBridgeService,
            AiRateLimitService aiRateLimitService,
            SecurityContextHelper securityContextHelper,
            AiChatProperties aiChatProperties,
            AiProviderProperties aiProviderProperties,
            AiNestProperties aiNestProperties,
            MeterRegistry meterRegistry,
            Optional<AuditEventProducer> auditEventProducer,
            I18nService i18nService,
            JwtUtils jwtUtils) {
        this(aiChatRepository, aiChatMessageRepository, aiKeyVaultService, transactionManager, semanticMemoryGraphService,
                null, nestStreamBridgeService, aiRateLimitService, securityContextHelper, aiChatProperties,
                aiProviderProperties, aiNestProperties, meterRegistry, auditEventProducer, i18nService, jwtUtils);
    }

    public AiChatService(AiChatRepository aiChatRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AiKeyVaultService aiKeyVaultService,
            SemanticMemoryGraphService semanticMemoryGraphService,
            NestStreamBridgeService nestStreamBridgeService,
            AiRateLimitService aiRateLimitService,
            SecurityContextHelper securityContextHelper,
            AiChatProperties aiChatProperties,
            AiProviderProperties aiProviderProperties,
            AiNestProperties aiNestProperties,
            MeterRegistry meterRegistry,
            Optional<AuditEventProducer> auditEventProducer,
            I18nService i18nService) {
        this(aiChatRepository, aiChatMessageRepository, aiKeyVaultService, null, semanticMemoryGraphService,
                null, nestStreamBridgeService, aiRateLimitService, securityContextHelper, aiChatProperties,
                aiProviderProperties, aiNestProperties, meterRegistry, auditEventProducer, i18nService, null);
    }

    @PostConstruct
    @SuppressWarnings("unused")
    void registerGauges() {
        meterRegistry.gauge("ai.chat.active-streams", activeStreamsGlobal);
    }

    @Transactional(readOnly = true)
    public List<McpChatResponseDto> listChats() {
        User currentUser = requireCurrentUser();
        return aiChatRepository.findByUserIdAndStatusOrderByLastMessageAtDesc(currentUser.getId(), AiChatStatus.ACTIVE)
                .stream()
                .map(this::toChatResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<McpChatMessageResponseDto> getChatHistory(Long chatId) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());
        return aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId())
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<McpChatMessageResponseDto> getChatHistory(Long chatId, Pageable pageable) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());

        Pageable normalized = pageable == null || pageable.isUnpaged()
                ? PageRequest.of(0, 30,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))
                : PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<McpChatMessageResponseDto> page = aiChatMessageRepository.findByChatId(chat.getId(), normalized)
                .map(this::toMessageResponse);

        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }

    public McpChatResponseDto updateChat(Long chatId, McpChatUpdateRequest request) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());

        String normalizedTitle = normalizeTitle(request != null ? request.title() : null);
        if (normalizedTitle == null) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AI_CHAT_TITLE_REQUIRED));
        }

        chat.setTitle(normalizedTitle);
        AiChat saved = aiChatRepository.save(chat);

        publishAudit(AiAuditEvent.builder()
                .eventType("AI_CHAT_UPDATED")
                .userId(currentUser.getId())
                .userName(currentUser.getName())
                .chatId(saved.getId())
                .provider(saved.getActiveProvider().name())
                .userLanguage(saved.getUserLanguage())
                .eventTimestamp(LocalDateTime.now())
                .build());

        return toChatResponse(saved);
    }

    public McpChatResponseDto createChat(McpChatCreateRequest request) {
        User currentUser = requireCurrentUser();

        if (!aiRateLimitService.canCreateChat(currentUser.getId())) {
            publishAudit(AiAuditEvent.builder()
                    .eventType("AI_RATE_LIMITED")
                    .userId(currentUser.getId())
                    .userName(currentUser.getName())
                    .errorType("max_chats")
                    .eventTimestamp(LocalDateTime.now())
                    .build());
            throw new AiChatLimitReachedException(i18nService.getMessage(MessageKey.ERROR_AI_CHAT_MAX_ACTIVE_REACHED));
        }

        AiProvider provider = resolveProvider(request != null ? request.provider() : null, true);
        String title = normalizeTitle(request != null ? request.title() : null);

        AiChat chat = AiChat.builder()
                .user(currentUser)
                .title(title)
                .status(AiChatStatus.ACTIVE)
                .activeProvider(provider)
                .userLanguage(aiChatProperties.getDefaultLanguage())
                .lastMessageAt(LocalDateTime.now())
                .build();

        AiChat created = aiChatRepository.save(chat);
        log.info("AI chat created: chatId={}, userId={}, provider={}", created.getId(), currentUser.getId(), provider);
        publishAudit(AiAuditEvent.builder()
                .eventType("AI_CHAT_CREATED")
                .userId(currentUser.getId())
                .userName(currentUser.getName())
                .chatId(created.getId())
                .provider(created.getActiveProvider().name())
                .userLanguage(created.getUserLanguage())
                .eventTimestamp(LocalDateTime.now())
                .build());
        return toChatResponse(created);
    }

    public McpChatResponseDto changeProvider(Long chatId, McpChangeProviderRequest request) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());

        AiProvider provider = resolveProvider(request != null ? request.provider() : null, true);
        boolean hasKey = aiKeyVaultService.listGlobalKeys().stream()
                .anyMatch(key -> key.provider() == provider && key.active());
        if (!hasKey) {
            throw new AiKeyNotFoundException(i18nService.getMessage(MessageKey.ERROR_AI_NO_GLOBAL_KEY, provider));
        }

        chat.setActiveProvider(provider);
        AiChat saved = aiChatRepository.save(chat);
        publishAudit(AiAuditEvent.builder()
                .eventType("AI_PROVIDER_CHANGED")
                .userId(currentUser.getId())
                .userName(currentUser.getName())
                .chatId(saved.getId())
                .provider(saved.getActiveProvider().name())
                .userLanguage(saved.getUserLanguage())
                .eventTimestamp(LocalDateTime.now())
                .build());
        return toChatResponse(saved);
    }

    public void archiveChat(Long chatId) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());
        chat.setStatus(AiChatStatus.ARCHIVED);
        aiChatRepository.save(chat);
        publishAudit(AiAuditEvent.builder()
                .eventType("AI_CHAT_ARCHIVED")
                .userId(currentUser.getId())
                .userName(currentUser.getName())
                .chatId(chat.getId())
                .provider(chat.getActiveProvider().name())
                .userLanguage(chat.getUserLanguage())
                .eventTimestamp(LocalDateTime.now())
                .build());
    }

    public SseEmitter sendMessage(Long chatId, McpChatMessageRequest request, String userJwt) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());

        // Validate JWT early before returning SseEmitter to detect authorization issues before streaming starts.
        if (jwtUtils != null && userJwt != null && !userJwt.isBlank()) {
            String username = jwtUtils.validateAndExtractUsername(userJwt);
            if (username == null) {
                log.warn("Invalid JWT token provided for AI chat streaming: chatId={}, userId={}", chatId,
                        currentUser.getId());
                throw new AiStreamException(i18nService.getMessage(MessageKey.ERROR_AUTH_JWT_INVALID));
            }
        }

        if (!aiRateLimitService.isAllowed(currentUser.getId())) {
            log.warn("AI rate limited: userId={}, reason={}", currentUser.getId(), "per_minute");
            publishAudit(AiAuditEvent.builder()
                    .eventType("AI_RATE_LIMITED")
                    .userId(currentUser.getId())
                    .userName(currentUser.getName())
                    .chatId(chat.getId())
                    .provider(chat.getActiveProvider().name())
                    .userLanguage(chat.getUserLanguage())
                    .errorType("per_minute")
                    .eventTimestamp(LocalDateTime.now())
                    .build());
            throw new AiRateLimitExceededException(i18nService.getMessage(MessageKey.ERROR_AI_RATE_LIMIT_EXCEEDED));
        }

        if (!aiRateLimitService.canSendMessage(chatId)) {
            log.warn("AI rate limited: userId={}, reason={}", currentUser.getId(), "max_messages");
            publishAudit(AiAuditEvent.builder()
                    .eventType("AI_RATE_LIMITED")
                    .userId(currentUser.getId())
                    .userName(currentUser.getName())
                    .chatId(chat.getId())
                    .provider(chat.getActiveProvider().name())
                    .userLanguage(chat.getUserLanguage())
                    .errorType("max_messages")
                    .eventTimestamp(LocalDateTime.now())
                    .build());
            if (Boolean.TRUE.equals(aiChatProperties.getAutoArchiveOnLimit())) {
                chat.setStatus(AiChatStatus.ARCHIVED);
                aiChatRepository.save(chat);
            }
            throw new AiMaxMessagesReachedException(i18nService.getMessage(MessageKey.ERROR_AI_MAX_MESSAGES_REACHED));
        }

        String content = request != null ? request.content() : null;
        if (content == null || content.isBlank()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AI_CHAT_MESSAGE_REQUIRED));
        }

        String language = resolveLanguage(request != null ? request.language() : null, chat.getUserLanguage());
        chat.setUserLanguage(language);

        AiChatMessage userMessage = AiChatMessage.builder()
                .chat(chat)
                .role(MessageRole.USER)
                .content(content.trim())
                .build();
        aiChatMessageRepository.save(userMessage);
        countMessage(MessageRole.USER, chat.getActiveProvider());
        publishAudit(AiAuditEvent.builder()
                .eventType("AI_MESSAGE_SENT")
                .userId(currentUser.getId())
                .userName(currentUser.getName())
                .chatId(chat.getId())
                .messageId(userMessage.getId())
                .provider(chat.getActiveProvider().name())
                .userLanguage(language)
                .eventTimestamp(LocalDateTime.now())
                .build());
        log.info("AI message sent: chatId={}, userId={}, language={}", chat.getId(), currentUser.getId(), language);

        String apiKey;
        try {
            apiKey = aiKeyVaultService.getDecryptedKey(chat.getActiveProvider());
        } catch (ResourceNotFoundException ex) {
            meterRegistry.counter("ai.chat.errors.total", "type", "no_key").increment();
            throw new AiKeyNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_AI_NO_GLOBAL_KEY, chat.getActiveProvider()));
        }

        List<AiChatMessage> history = aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId());
        CompressedContext compressedContext = semanticMemoryGraphService.compress(history, language);
        String systemPrompt = buildSystemPrompt(currentUser.getName(), language);

        String model = resolveModel(chat.getActiveProvider());
        NestCompletionRequest nestRequest = new NestCompletionRequest(
                compressedContext.toPromptString(),
                systemPrompt,
                apiKey,
                chat.getActiveProvider().name(),
                currentUser.getName(),
                language,
                model);

        SseEmitter emitter = new SseEmitter(aiNestProperties.getStreamTimeoutMs());
        StreamLease lease = acquireStreamLease(currentUser.getId());
        // Capture current security context to propagate to virtual thread.
        SecurityContext securityContext = SecurityContextHolder.getContext();


        Thread.startVirtualThread(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            long streamStart = System.nanoTime();
            // Restore security context in virtual thread.
            SecurityContextHolder.setContext(securityContext);
            try {
                NestStreamBridgeService.StreamCompletionResult result = nestStreamBridgeService
                        .streamCompletion(nestRequest, emitter, userJwt);

                int inputTokens = safeInt(result.inputTokens());
                int outputTokens = safeInt(result.outputTokens());
                String assistantContent = result.fullResponse() == null ? "" : result.fullResponse();
                String thinkingContent = result.thinkingContent();
                var toolCalls = result.toolCalls();

                executeAssistantCompletion(chat, currentUser, language, assistantContent, thinkingContent, toolCalls,
                        inputTokens, outputTokens, compressedContext, streamStart);
            } catch (RuntimeException ex) {
                log.error("AI stream error for chat {}: {}", chatId, ex.getMessage());
                meterRegistry.counter("ai.chat.errors.total", "type", resolveErrorType(ex)).increment();
                publishAudit(AiAuditEvent.builder()
                        .eventType("AI_STREAM_ERROR")
                        .userId(currentUser.getId())
                        .userName(currentUser.getName())
                        .chatId(chat.getId())
                        .provider(chat.getActiveProvider().name())
                        .userLanguage(language)
                        .errorType(resolveErrorType(ex))
                        .eventTimestamp(LocalDateTime.now())
                        .build());
                persistSystemError(chat, currentUser, ex);
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                    // Ignore emitter completion race.
                }
            } finally {
                releaseStreamLease(currentUser.getId(), lease);
                sample.stop(
                        meterRegistry.timer("ai.chat.stream.duration", "provider", chat.getActiveProvider().name()));
            }
        });

        return emitter;
    }

    private void executeAssistantCompletion(AiChat chat,
            User currentUser,
            String language,
            String assistantContent,
            String thinkingContent,
            java.util.List<ToolCallInfo> toolCalls,
            int inputTokens,
            int outputTokens,
            CompressedContext compressedContext,
            long streamStart) {
        Runnable completionWork = () -> {
            AiChat managedChat = aiChatRepository.findByIdAndUserId(chat.getId(), currentUser.getId())
                    .orElseThrow(() -> new AiChatNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

            AiChatMessage assistantMessage = AiChatMessage.builder()
                    .chat(managedChat)
                    .role(MessageRole.ASSISTANT)
                    .content(assistantContent)
                    .thinkingContent(thinkingContent)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .build();
            aiChatMessageRepository.save(assistantMessage);

            // Persist tool calls as separate TOOL messages
            if (toolCalls != null) {
                List<AiChatMessage> toolMessages = new ArrayList<>(toolCalls.size());
                for (ToolCallInfo tc : toolCalls) {
                    AiChatMessage toolMessage = AiChatMessage.builder()
                            .chat(managedChat)
                            .role(MessageRole.TOOL)
                            .toolName(tc.toolName())
                            .toolCallId(tc.toolCallId())
                            .toolResult(tc.toolResult())
                            .build();
                    toolMessages.add(toolMessage);
                }
                if (!toolMessages.isEmpty()) {
                    aiChatMessageRepository.saveAll(toolMessages);
                }
            }

            countMessage(MessageRole.ASSISTANT, managedChat.getActiveProvider());
            countTokens(managedChat.getActiveProvider(), inputTokens, outputTokens);

            managedChat.setLastMessageAt(LocalDateTime.now());
            managedChat.setMessageCount(managedChat.getMessageCount() + 2 + (toolCalls != null ? toolCalls.size() : 0));
            managedChat.setTotalTokensConsumed(managedChat.getTotalTokensConsumed() + inputTokens + outputTokens);
            aiChatRepository.save(managedChat);

            long streamDurationMs = nanosToMillis(streamStart);
            log.info("AI stream completed: chatId={}, tokens={}/{}, duration={}ms, compression={}",
                    managedChat.getId(), inputTokens, outputTokens, streamDurationMs,
                    compressedContext.compressionRatio());
            publishAudit(AiAuditEvent.builder()
                    .eventType("AI_MESSAGE_RECEIVED")
                    .userId(currentUser.getId())
                    .userName(currentUser.getName())
                    .chatId(managedChat.getId())
                    .messageId(assistantMessage.getId())
                    .provider(managedChat.getActiveProvider().name())
                    .userLanguage(language)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .streamDurationMs(streamDurationMs)
                    .compressionRatio(compressedContext.compressionRatio())
                    .eventTimestamp(LocalDateTime.now())
                    .build());

            aiRateLimitService.recordRequest(currentUser.getId());
        };

        if (transactionManager == null) {
            completionWork.run();
            return;
        }

        new TransactionTemplate(transactionManager).execute(status -> {
            completionWork.run();
            return null;
        });
    }

    public McpApiKeyResponseDto saveApiKey(McpApiKeyRequest request) {
        User currentUser = requireCurrentUser();
        AiProvider provider = resolveProvider(request != null ? request.provider() : null, false);
        String apiKey = request != null ? request.apiKey() : null;
        aiKeyVaultService.saveKey(currentUser.getId(), provider, apiKey);
        return aiKeyVaultService.listKeys(currentUser.getId()).stream()
                .filter(key -> key.provider() == provider)
                .findFirst()
                .map(this::toApiKeyResponse)
                .orElseThrow(() -> new AiKeyNotFoundException(i18nService.getMessage(MessageKey.ERROR_AI_KEY_NOT_FOUND)));
    }

    public McpApiKeyResponseDto updateApiKey(McpApiKeyRequest request) {
        User currentUser = requireCurrentUser();
        AiProvider provider = resolveProvider(request != null ? request.provider() : null, false);
        String apiKey = request != null ? request.apiKey() : null;
        aiKeyVaultService.updateKey(currentUser.getId(), provider, apiKey);
        return aiKeyVaultService.listKeys(currentUser.getId()).stream()
                .filter(key -> key.provider() == provider)
                .findFirst()
                .map(this::toApiKeyResponse)
                .orElseThrow(() -> new AiKeyNotFoundException(i18nService.getMessage(MessageKey.ERROR_AI_KEY_NOT_FOUND)));
    }

    public List<McpApiKeyResponseDto> listApiKeys() {
        User currentUser = requireCurrentUser();
        return aiKeyVaultService.listKeys(currentUser.getId()).stream()
                .map(this::toApiKeyResponse)
                .toList();
    }

    public void deleteApiKey(Long keyId) {
        User currentUser = requireCurrentUser();
        aiKeyVaultService.deleteKey(currentUser.getId(), keyId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> listEnabledProviders() {
        return aiProviderProperties.getConfigs().entrySet().stream()
                .filter(entry -> !Boolean.FALSE.equals(entry.getValue().getEnabled()))
                .map(entry -> Map.of(
                        "name", entry.getKey(),
                        "displayName", defaultText(entry.getValue().getDisplayName()),
                        "modelDefault", defaultText(entry.getValue().getModelDefault())))
                .toList();
    }

    private AiProvider resolveProvider(String providerValue, boolean allowDefault) {
        String raw = providerValue;
        if ((raw == null || raw.isBlank()) && allowDefault) {
            raw = aiChatProperties.getDefaultProvider();
        }
        if (raw == null || raw.isBlank()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AI_PROVIDER_REQUIRED));
        }

        final AiProvider provider;
        try {
            provider = AiProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new AiProviderDisabledException(i18nService.getMessage(MessageKey.ERROR_AI_PROVIDER_DISABLED, raw));
        }

        AiProviderProperties.ProviderConfig providerConfig = aiProviderProperties.getConfigs().get(provider.name());
        if (providerConfig == null || Boolean.FALSE.equals(providerConfig.getEnabled())) {
            throw new AiProviderDisabledException(i18nService.getMessage(MessageKey.ERROR_AI_PROVIDER_DISABLED, provider.name()));
        }

        return provider;
    }

    private String resolveModel(AiProvider provider) {
        AiProviderProperties.ProviderConfig providerConfig = aiProviderProperties.getConfigs().get(provider.name());
        if (providerConfig == null || providerConfig.getModelDefault() == null
                || providerConfig.getModelDefault().isBlank()) {
            return "";
        }
        return providerConfig.getModelDefault();
    }

    private String resolveLanguage(String requestedLanguage, String chatLanguage) {
        List<String> supported = aiChatProperties.getSupportedLanguages();
        if (requestedLanguage != null && !requestedLanguage.isBlank()) {
            String normalized = requestedLanguage.toLowerCase(Locale.ROOT);
            if (supported.contains(normalized)) {
                return normalized;
            }
            return aiChatProperties.getDefaultLanguage();
        }
        if (chatLanguage != null && !chatLanguage.isBlank()) {
            return chatLanguage;
        }
        return aiChatProperties.getDefaultLanguage();
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > aiChatProperties.getTitleMaxLength()) {
            return trimmed.substring(0, aiChatProperties.getTitleMaxLength());
        }
        return trimmed;
    }

    private AiChat getOwnedChat(Long chatId, Integer userId) {
        return aiChatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new AiChatNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
    }

    private User requireCurrentUser() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_USER_REQUIRED));
        }
        return currentUser;
    }

    private void countMessage(MessageRole role, AiProvider provider) {
        counter("ai.chat.messages.total", "role", role.name(), "provider", provider.name()).increment();
    }

    private void countTokens(AiProvider provider, int inputTokens, int outputTokens) {
        counter("ai.chat.tokens.total", "direction", "input", "provider", provider.name()).increment(inputTokens);
        counter("ai.chat.tokens.total", "direction", "output", "provider", provider.name()).increment(outputTokens);
    }

    private void persistSystemError(AiChat chat, User currentUser, Exception ex) {
        Runnable errorWork = () -> {
            AiChat managedChat = aiChatRepository.findByIdAndUserId(chat.getId(), currentUser.getId())
                    .orElseThrow(() -> new AiChatNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

            AiChatMessage systemMessage = AiChatMessage.builder()
                    .chat(managedChat)
                    .role(MessageRole.SYSTEM)
                    .content(i18nService.getMessage(MessageKey.ERROR_AI_STREAM_PREFIX) + ex.getMessage())
                    .build();
            aiChatMessageRepository.save(systemMessage);
            managedChat.setLastMessageAt(LocalDateTime.now());
            managedChat.setMessageCount(managedChat.getMessageCount() + 1);
            aiChatRepository.save(managedChat);
            countMessage(MessageRole.SYSTEM, managedChat.getActiveProvider());
        };

        try {
            if (transactionManager == null) {
                errorWork.run();
                return;
            }

            new TransactionTemplate(transactionManager).execute(status -> {
                errorWork.run();
                return null;
            });
        } catch (Exception ignored) {
            // Ignore secondary persistence failures in error path.
        }
    }

    private Counter counter(String name, String... tags) {
        return meterRegistry.counter(name, tags);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String buildSystemPrompt(String userName, String language) {
        var systemContext = mcpUtilityService != null ? mcpUtilityService.getSystemContext() : null;
        String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String normalizedName = userName == null || userName.isBlank() ? "compa" : userName.trim();
        String normalizedLanguage = language == null || language.isBlank() ? aiChatProperties.getDefaultLanguage()
                : language.trim();

        long totalProducts = systemContext != null ? systemContext.getTotalProducts() : 0L;
        long pendingOrders = systemContext != null ? systemContext.getPendingOrdersCount() : 0L;
        long totalRecipes = systemContext != null ? systemContext.getTotalRecipes() : 0L;
        long activeAlerts = systemContext != null ? systemContext.getActiveAlertsCount() : 0L;

        return String.format(Locale.ROOT,
                aiChatProperties.getSystemPromptTemplate(),
                normalizedName,
                normalizedName,
                formattedDate,
            totalProducts,
            pendingOrders,
            totalRecipes,
            activeAlerts,
                normalizedLanguage);
    }

    private String resolveErrorType(Exception ex) {
        if (ex instanceof AiConcurrentStreamException) {
            return "rate_limit";
        }
        if (ex instanceof AiRateLimitExceededException || ex instanceof AiMaxMessagesReachedException) {
            return "rate_limit";
        }
        if (ex instanceof AiKeyNotFoundException) {
            return "no_key";
        }
        if (ex instanceof AiStreamException) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
            return message.contains("timeout") ? "timeout" : "nest_down";
        }
        return "unknown";
    }

    private long nanosToMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private void publishAudit(AiAuditEvent event) {
        auditEventProducer.ifPresent(producer -> producer.publishAiAudit(event));
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private McpChatResponseDto toChatResponse(AiChat chat) {
        return new McpChatResponseDto(
                chat.getId(),
                chat.getTitle(),
                chat.getStatus().name(),
                chat.getActiveProvider().name(),
                chat.getUserLanguage(),
                chat.getCreatedAt(),
                chat.getLastMessageAt(),
                chat.getMessageCount());
    }

    private McpChatMessageResponseDto toMessageResponse(AiChatMessage message) {
        return new McpChatMessageResponseDto(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getToolName(),
                message.getToolCallId(),
                message.getToolResult(),
                message.getThinkingContent(),
                message.getInputTokens(),
                message.getOutputTokens(),
                message.getCreatedAt());
    }

    private McpApiKeyResponseDto toApiKeyResponse(AiKeyVaultService.ApiKeyMetadata metadata) {
        return new McpApiKeyResponseDto(
                metadata.id(),
                metadata.provider().name(),
                metadata.keyHint(),
                metadata.active(),
                metadata.createdAt());
    }

    private StreamLease acquireStreamLease(Integer userId) {
        AtomicInteger byUser = activeStreamsByUser.computeIfAbsent(userId, ignored -> new AtomicInteger(0));
        int userCount = byUser.incrementAndGet();
        if (userCount > aiChatProperties.getMaxConcurrentStreamsPerUser()) {
            byUser.decrementAndGet();
            throw new AiConcurrentStreamException(i18nService.getMessage(MessageKey.ERROR_AI_CONCURRENT_STREAMS_REACHED));
        }

        activeStreamsGlobal.incrementAndGet();
        return new StreamLease();
    }

    private void releaseStreamLease(Integer userId, StreamLease lease) {
        if (!lease.released.compareAndSet(false, true)) {
            return;
        }

        AtomicInteger byUser = activeStreamsByUser.get(userId);
        if (byUser != null) {
            int remaining = byUser.decrementAndGet();
            if (remaining <= 0) {
                activeStreamsByUser.remove(userId, byUser);
            }
        }
        activeStreamsGlobal.updateAndGet(value -> Math.max(0, value - 1));
    }

    private static class StreamLease {
        private final AtomicBoolean released = new AtomicBoolean(false);
    }
}
