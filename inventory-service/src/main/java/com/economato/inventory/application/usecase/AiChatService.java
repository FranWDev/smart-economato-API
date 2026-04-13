package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.mcp.McpApiKeyRequest;
import com.economato.inventory.application.dto.mcp.McpApiKeyResponseDto;
import com.economato.inventory.application.dto.mcp.McpChangeProviderRequest;
import com.economato.inventory.application.dto.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.mcp.McpChatMessageResponseDto;
import com.economato.inventory.application.dto.mcp.McpChatResponseDto;
import com.economato.inventory.application.dto.mcp.NestCompletionRequest;
import com.economato.inventory.application.usecase.smg.SemanticMemoryGraphService;
import com.economato.inventory.application.usecase.smg.model.CompressedContext;
import com.economato.inventory.domain.model.AiChat;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.AiChatStatus;
import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.AiStreamException;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiChatLimitReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiChatNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiConcurrentStreamException;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiChatService {

    private final AiChatRepository aiChatRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiKeyVaultService aiKeyVaultService;
    private final SemanticMemoryGraphService semanticMemoryGraphService;
    private final NestStreamBridgeService nestStreamBridgeService;
    private final AiRateLimitService aiRateLimitService;
    private final SecurityContextHelper securityContextHelper;
    private final AiChatProperties aiChatProperties;
    private final AiProviderProperties aiProviderProperties;
    private final AiNestProperties aiNestProperties;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<Integer, AtomicInteger> activeStreamsByUser = new ConcurrentHashMap<>();
    private final AtomicInteger activeStreamsGlobal = new AtomicInteger(0);

    @PostConstruct
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

    public McpChatResponseDto createChat(McpChatCreateRequest request) {
        User currentUser = requireCurrentUser();

        if (!aiRateLimitService.canCreateChat(currentUser.getId())) {
            throw new AiChatLimitReachedException("Maximum active chats reached for this user");
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

        return toChatResponse(aiChatRepository.save(chat));
    }

    public McpChatResponseDto changeProvider(Long chatId, McpChangeProviderRequest request) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());

        AiProvider provider = resolveProvider(request != null ? request.provider() : null, true);
        boolean hasKey = aiKeyVaultService.listKeys(currentUser.getId()).stream()
                .anyMatch(key -> key.provider() == provider && key.active());
        if (!hasKey) {
            throw new AiKeyNotFoundException("No API key configured for provider " + provider);
        }

        chat.setActiveProvider(provider);
        return toChatResponse(aiChatRepository.save(chat));
    }

    public void archiveChat(Long chatId) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());
        chat.setStatus(AiChatStatus.ARCHIVED);
        aiChatRepository.save(chat);
    }

    public SseEmitter sendMessage(Long chatId, McpChatMessageRequest request, String userJwt) {
        User currentUser = requireCurrentUser();
        AiChat chat = getOwnedChat(chatId, currentUser.getId());

        if (!aiRateLimitService.isAllowed(currentUser.getId())) {
            throw new AiRateLimitExceededException("AI message rate limit exceeded");
        }

        if (!aiRateLimitService.canSendMessage(chatId)) {
            if (Boolean.TRUE.equals(aiChatProperties.getAutoArchiveOnLimit())) {
                chat.setStatus(AiChatStatus.ARCHIVED);
                aiChatRepository.save(chat);
            }
            throw new AiMaxMessagesReachedException("Maximum messages reached for this chat");
        }

        String content = request != null ? request.content() : null;
        if (content == null || content.isBlank()) {
            throw new InvalidOperationException("Message content is required");
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

        String apiKey;
        try {
            apiKey = aiKeyVaultService.getDecryptedKey(currentUser.getId(), chat.getActiveProvider());
        } catch (ResourceNotFoundException ex) {
            throw new AiKeyNotFoundException("No API key configured for provider " + chat.getActiveProvider());
        }

        List<AiChatMessage> history = aiChatMessageRepository.findByChatIdOrderByCreatedAtAsc(chat.getId());
        CompressedContext compressedContext = semanticMemoryGraphService.compress(history, language);

        String model = resolveModel(chat.getActiveProvider());
        NestCompletionRequest nestRequest = new NestCompletionRequest(
                compressedContext.toPromptString(),
                apiKey,
                chat.getActiveProvider().name(),
                currentUser.getName(),
                language,
                model
        );

        SseEmitter emitter = new SseEmitter(aiNestProperties.getStreamTimeoutMs());
        StreamLease lease = acquireStreamLease(currentUser.getId());

        Thread.startVirtualThread(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                NestStreamBridgeService.StreamCompletionResult result = nestStreamBridgeService
                        .streamCompletion(nestRequest, emitter, userJwt);

                int inputTokens = safeInt(result.inputTokens());
                int outputTokens = safeInt(result.outputTokens());
                String assistantContent = result.fullResponse() == null ? "" : result.fullResponse();

                AiChatMessage assistantMessage = AiChatMessage.builder()
                        .chat(chat)
                        .role(MessageRole.ASSISTANT)
                        .content(assistantContent)
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .build();
                aiChatMessageRepository.save(assistantMessage);
                countMessage(MessageRole.ASSISTANT, chat.getActiveProvider());

                chat.setLastMessageAt(LocalDateTime.now());
                chat.setMessageCount(chat.getMessageCount() + 2);
                chat.setTotalTokensConsumed(chat.getTotalTokensConsumed() + inputTokens + outputTokens);
                aiChatRepository.save(chat);

                aiRateLimitService.recordRequest(currentUser.getId());
            } catch (Exception ex) {
                log.error("AI stream error for chat {}: {}", chatId, ex.getMessage());
                meterRegistry.counter("ai.chat.errors.total", "type", resolveErrorType(ex)).increment();
                persistSystemError(chat, ex);
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                    // Ignore emitter completion race.
                }
            } finally {
                releaseStreamLease(currentUser.getId(), lease);
                sample.stop(meterRegistry.timer("ai.chat.stream.duration", "provider", chat.getActiveProvider().name()));
            }
        });

        return emitter;
    }

    public McpApiKeyResponseDto saveApiKey(McpApiKeyRequest request) {
        User currentUser = requireCurrentUser();
        AiProvider provider = resolveProvider(request != null ? request.provider() : null, false);
        aiKeyVaultService.saveKey(currentUser.getId(), provider, request.apiKey());
        return aiKeyVaultService.listKeys(currentUser.getId()).stream()
                .filter(key -> key.provider() == provider)
                .findFirst()
                .map(this::toApiKeyResponse)
                .orElseThrow(() -> new AiKeyNotFoundException("API key could not be saved"));
    }

    public McpApiKeyResponseDto updateApiKey(McpApiKeyRequest request) {
        User currentUser = requireCurrentUser();
        AiProvider provider = resolveProvider(request != null ? request.provider() : null, false);
        aiKeyVaultService.updateKey(currentUser.getId(), provider, request.apiKey());
        return aiKeyVaultService.listKeys(currentUser.getId()).stream()
                .filter(key -> key.provider() == provider)
                .findFirst()
                .map(this::toApiKeyResponse)
                .orElseThrow(() -> new AiKeyNotFoundException("API key could not be updated"));
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
                        "modelDefault", defaultText(entry.getValue().getModelDefault())
                ))
                .toList();
    }

    private AiProvider resolveProvider(String providerValue, boolean allowDefault) {
        String raw = providerValue;
        if ((raw == null || raw.isBlank()) && allowDefault) {
            raw = aiChatProperties.getDefaultProvider();
        }
        if (raw == null || raw.isBlank()) {
            throw new InvalidOperationException("Provider is required");
        }

        final AiProvider provider;
        try {
            provider = AiProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new AiProviderDisabledException("Unsupported provider: " + raw);
        }

        AiProviderProperties.ProviderConfig providerConfig = aiProviderProperties.getConfigs().get(provider.name());
        if (providerConfig == null || Boolean.FALSE.equals(providerConfig.getEnabled())) {
            throw new AiProviderDisabledException("Provider disabled: " + provider.name());
        }

        return provider;
    }

    private String resolveModel(AiProvider provider) {
        AiProviderProperties.ProviderConfig providerConfig = aiProviderProperties.getConfigs().get(provider.name());
        if (providerConfig == null || providerConfig.getModelDefault() == null || providerConfig.getModelDefault().isBlank()) {
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
                .orElseThrow(() -> new AiChatNotFoundException("AI chat not found"));
    }

    private User requireCurrentUser() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new InvalidOperationException("Authenticated user is required");
        }
        return currentUser;
    }

    private void countMessage(MessageRole role, AiProvider provider) {
        counter("ai.chat.messages.total", "role", role.name(), "provider", provider.name()).increment();
    }

    private void persistSystemError(AiChat chat, Exception ex) {
        try {
            AiChatMessage systemMessage = AiChatMessage.builder()
                    .chat(chat)
                    .role(MessageRole.SYSTEM)
                    .content("Stream error: " + ex.getMessage())
                    .build();
            aiChatMessageRepository.save(systemMessage);
            chat.setLastMessageAt(LocalDateTime.now());
            chat.setMessageCount(chat.getMessageCount() + 1);
            aiChatRepository.save(chat);
            countMessage(MessageRole.SYSTEM, chat.getActiveProvider());
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

    private String resolveErrorType(Exception ex) {
        if (ex instanceof AiConcurrentStreamException) {
            return "concurrent_stream";
        }
        if (ex instanceof AiRateLimitExceededException || ex instanceof AiMaxMessagesReachedException) {
            return "rate_limit";
        }
        if (ex instanceof AiStreamException) {
            return "nest_stream";
        }
        return "unknown";
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
                chat.getMessageCount()
        );
    }

    private McpChatMessageResponseDto toMessageResponse(AiChatMessage message) {
        return new McpChatMessageResponseDto(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getToolName(),
                message.getToolResult(),
                message.getInputTokens(),
                message.getOutputTokens(),
                message.getCreatedAt()
        );
    }

    private McpApiKeyResponseDto toApiKeyResponse(AiKeyVaultService.ApiKeyMetadata metadata) {
        return new McpApiKeyResponseDto(
                metadata.id(),
                metadata.provider().name(),
                metadata.keyHint(),
                metadata.active(),
                metadata.createdAt()
        );
    }

    private StreamLease acquireStreamLease(Integer userId) {
        AtomicInteger byUser = activeStreamsByUser.computeIfAbsent(userId, ignored -> new AtomicInteger(0));
        int userCount = byUser.incrementAndGet();
        if (userCount > aiChatProperties.getMaxConcurrentStreamsPerUser()) {
            byUser.decrementAndGet();
            throw new AiConcurrentStreamException("Maximum concurrent AI streams reached for this user");
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
