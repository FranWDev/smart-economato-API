package com.economato.inventory.application.usecase.ai;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.ai.event.AiAuditEvent;
import com.economato.inventory.application.dto.user.mcp.McpApiKeyRequest;
import com.economato.inventory.application.dto.user.mcp.McpApiKeyResponseDto;
import com.economato.inventory.application.dto.mcp.mcp.McpChangeProviderRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageResponseDto;
import com.economato.inventory.application.dto.mcp.mcp.McpChatResponseDto;
import com.economato.inventory.application.dto.mcp.mcp.McpChatUpdateRequest;
import com.economato.inventory.domain.model.ai.AiChat;
import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.ai.AiChatStatus;
import com.economato.inventory.domain.model.ai.AiProvider;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiChatLimitReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiChatNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiKeyNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiProviderDisabledException;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai.AiChatRepository;
import com.economato.inventory.infrastructure.config.ai.ai.AiChatProperties;
import com.economato.inventory.infrastructure.config.ai.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiChatHistoryService {

    private final AiChatRepository aiChatRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiKeyVaultService aiKeyVaultService;
    private final SecurityContextHelper securityContextHelper;
    private final AiChatProperties aiChatProperties;
    private final AiProviderProperties aiProviderProperties;
    private final AiRateLimitService aiRateLimitService;
    private final Optional<AuditEventProducer> auditEventProducer;
    private final I18nService i18nService;

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

    public AiProvider resolveProvider(String providerValue, boolean allowDefault) {
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

    public AiChat getOwnedChat(Long chatId, Integer userId) {
        return aiChatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new AiChatNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
    }

    public User requireCurrentUser() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_USER_REQUIRED));
        }
        return currentUser;
    }

    private void publishAudit(AiAuditEvent event) {
        auditEventProducer.ifPresent(producer -> producer.publishAiAudit(event));
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

    private String defaultText(String value) {
        return value == null ? "" : value;
    }
}
