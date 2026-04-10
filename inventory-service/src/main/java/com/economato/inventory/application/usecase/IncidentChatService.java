package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.response.ChatReadReceiptBroadcastDTO;
import com.economato.inventory.application.dto.response.IncidentChatTypingResponseDTO;
import com.economato.inventory.application.dto.response.IncidentChatMessageResponseDTO;
import com.economato.inventory.application.mapper.IncidentChatReadReceiptMapper;
import com.economato.inventory.application.mapper.IncidentChatMessageMapper;
import com.economato.inventory.domain.model.Incident;
import com.economato.inventory.domain.model.IncidentChatMessage;
import com.economato.inventory.domain.model.IncidentChatReadReceipt;
import com.economato.inventory.domain.model.IncidentStatus;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentChatReadReceiptRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class IncidentChatService {

    private static final int MAX_CHAT_CONTENT_LENGTH = 5000;

    private final IncidentRepository incidentRepository;
    private final IncidentChatMessageRepository incidentChatMessageRepository;
    private final IncidentChatReadReceiptRepository readReceiptRepository;
    private final SecurityContextHelper securityContextHelper;
    private final IncidentParticipantService incidentParticipantService;
    private final IncidentChatMessageMapper incidentChatMessageMapper;
    private final IncidentChatReadReceiptMapper readReceiptMapper;
    private final FileStorageService fileStorageService;
    private final PersistentNotificationService persistentNotificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final I18nService i18nService;

    public IncidentChatMessageResponseDTO sendMessage(Long incidentId, String content, MultipartFile file) {
        Incident incident = getIncidentOrThrow(incidentId);
        User currentUser = getCurrentUserOrThrow();
        ensureParticipant(incident, currentUser);

        if (isClosed(incident)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_INVALID_STATE));
        }

        String normalizedContent = content == null ? null : content.trim();
        if (normalizedContent != null && normalizedContent.length() > MAX_CHAT_CONTENT_LENGTH) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_CHAT_MESSAGE_TOO_LONG));
        }

        boolean hasText = normalizedContent != null && !normalizedContent.isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasText && !hasFile) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_CHAT_EMPTY_MESSAGE));
        }

        String storedAttachmentPath = null;
        try {
            IncidentChatMessage message = IncidentChatMessage.builder()
                    .incident(incident)
                    .author(currentUser)
                    .content(hasText ? normalizedContent : null)
                    .hasAttachment(false)
                    .build();

            IncidentChatMessage saved = incidentChatMessageRepository.save(message);

            if (hasFile) {
                storedAttachmentPath = fileStorageService.store(incidentId, saved.getId(), file);
                saved.setHasAttachment(true);
                saved.setAttachmentUrl(storedAttachmentPath);
                String attachmentFilename = null;
                if (file != null) {
                    attachmentFilename = file.getOriginalFilename();
                }
                saved.setAttachmentFilename(attachmentFilename);
                saved.setAttachmentContentType(file != null ? file.getContentType() : null);
                saved = incidentChatMessageRepository.save(saved);
            }

            IncidentChatReadReceipt authorReceipt = saveOrUpdateReadReceipt(incident, currentUser, saved.getId());

            IncidentChatMessageResponseDTO response = incidentChatMessageMapper.toResponseDTO(saved, List.of(authorReceipt));
            persistentNotificationService.notifyIncidentChatMessage(incident, currentUser);
            messagingTemplate.convertAndSend("/topic/incidents/" + incidentId + "/chat", response);

            return response;
        } catch (RuntimeException ex) {
            if (storedAttachmentPath != null) {
                fileStorageService.delete(storedAttachmentPath);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<IncidentChatMessageResponseDTO> getHistory(Long incidentId, Pageable pageable) {
        Incident incident = getIncidentOrThrow(incidentId);
        User currentUser = getCurrentUserOrThrow();
        ensureParticipant(incident, currentUser);

        Pageable normalized = pageable == null
                ? PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt"))
                : pageable;

        List<IncidentChatReadReceipt> readReceipts = readReceiptRepository.findByIncidentId(incidentId);

        Page<IncidentChatMessageResponseDTO> page = incidentChatMessageRepository
                .findByIncidentIdOrderByCreatedAtAsc(incidentId, normalized)
                .map(message -> incidentChatMessageMapper.toResponseDTO(message, readReceipts));

        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }

    @Transactional
    public void markMessagesAsRead(Long incidentId) {
        Incident incident = getIncidentOrThrow(incidentId);
        User currentUser = getCurrentUserOrThrow();
        ensureParticipant(incident, currentUser);

        IncidentChatMessage lastMessage = incidentChatMessageRepository.findTopByIncidentIdOrderByIdDesc(incidentId)
                .orElse(null);
        if (lastMessage == null) {
            return;
        }

        Optional<IncidentChatReadReceipt> existingReceipt = readReceiptRepository.findByIncidentIdAndUserId(incidentId, currentUser.getId());
        if (existingReceipt.isPresent() && existingReceipt.get().getLastReadMessageId() >= lastMessage.getId()) {
            return;
        }

        IncidentChatReadReceipt receipt = saveOrUpdateReadReceipt(incident, currentUser, lastMessage.getId());
        ChatReadReceiptBroadcastDTO broadcastDTO = readReceiptMapper.toBroadcastDTO(receipt, incidentId);
        messagingTemplate.convertAndSend("/topic/incidents/" + incidentId + "/chat/read-receipts", broadcastDTO);
    }

    @Transactional(readOnly = true)
    public void broadcastTyping(Long incidentId, boolean typing) {
        Incident incident = getIncidentOrThrow(incidentId);
        User currentUser = getCurrentUserOrThrow();
        ensureParticipant(incident, currentUser);

        IncidentChatTypingResponseDTO typingDTO = IncidentChatTypingResponseDTO.builder()
                .incidentId(incidentId)
                .userId(currentUser.getId())
                .userName(currentUser.getName())
                .typing(typing)
                .build();

        messagingTemplate.convertAndSend("/topic/incidents/" + incidentId + "/chat/typing", typingDTO);
    }

    @Transactional(readOnly = true)
    public Resource downloadAttachment(Long incidentId, Long messageId) {
        Incident incident = getIncidentOrThrow(incidentId);
        User currentUser = getCurrentUserOrThrow();
        ensureParticipant(incident, currentUser);

        IncidentChatMessage message = incidentChatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        if (!message.getIncident().getId().equals(incidentId) || !message.isHasAttachment() || message.getAttachmentUrl() == null) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
        }

        return fileStorageService.load(message.getAttachmentUrl());
    }

    @Transactional(readOnly = true)
    public IncidentChatMessageResponseDTO getMessage(Long incidentId, Long messageId) {
        Incident incident = getIncidentOrThrow(incidentId);
        User currentUser = getCurrentUserOrThrow();
        ensureParticipant(incident, currentUser);

        IncidentChatMessage message = incidentChatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        if (!message.getIncident().getId().equals(incidentId)) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
        }

        return incidentChatMessageMapper.toResponseDTO(message);
    }

    private IncidentChatReadReceipt saveOrUpdateReadReceipt(Incident incident, User user, Long lastReadMessageId) {
        LocalDateTime now = LocalDateTime.now();
        IncidentChatReadReceipt receipt = readReceiptRepository.findByIncidentIdAndUserId(incident.getId(), user.getId())
                .orElseGet(() -> IncidentChatReadReceipt.builder()
                        .incident(incident)
                        .user(user)
                        .build());

        receipt.setIncident(incident);
        receipt.setUser(user);
        receipt.setLastReadMessageId(lastReadMessageId);
        receipt.setReadAt(now);

        return readReceiptRepository.save(receipt);
    }

    private Incident getIncidentOrThrow(Long incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_NOT_FOUND)));
    }

    private User getCurrentUserOrThrow() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
        }
        return currentUser;
    }

    private void ensureParticipant(Incident incident, User user) {
        if (!incidentParticipantService.isParticipant(incident, user)) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_NOT_PARTICIPANT));
        }
    }

    private boolean isClosed(Incident incident) {
        return incident.getStatus() == IncidentStatus.CERRADO_CON_RESOLUCION
                || incident.getStatus() == IncidentStatus.CERRADO_SIN_RESOLUCION;
    }
}
