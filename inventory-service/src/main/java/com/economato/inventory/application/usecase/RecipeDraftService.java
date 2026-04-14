package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.request.RecipeDraftRejectRequestDTO;
import com.economato.inventory.application.dto.request.RecipeDraftRequestDTO;
import com.economato.inventory.application.dto.request.RecipeRequestDTO;
import com.economato.inventory.application.dto.response.RecipeDraftResponseDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.application.mapper.RecipeDraftMapper;
import com.economato.inventory.domain.model.NotificationType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.RecipeDraft;
import com.economato.inventory.domain.model.RecipeDraftStatus;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeDraftRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class RecipeDraftService {

    private final RecipeDraftRepository recipeDraftRepository;
    private final RecipeService recipeService;
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RecipeDraftMapper recipeDraftMapper;
    private final PersistentNotificationService persistentNotificationService;

    public RecipeDraftResponseDTO createDraft(RecipeDraftRequestDTO dto) {
        User currentUser = getCurrentUserOrThrow();
        validateProducts(dto.getComponents());

        RecipeDraft draft = RecipeDraft.builder()
                .name(dto.getName())
                .elaboration(dto.getElaboration())
                .presentation(dto.getPresentation())
                .portions(dto.getPortions())
                .componentsJson(writeJson(dto.getComponents()))
                .allergenIdsJson(writeJson(dto.getAllergenIds()))
                .isHidden(dto.isHidden())
                .createdBy(currentUser)
                .status(RecipeDraftStatus.PENDING)
                .build();

        RecipeDraft saved = recipeDraftRepository.save(draft);
        notifyAdmins(saved, currentUser, MessageKey.NOTIFICATION_RECIPE_DRAFT_SUBMITTED, NotificationType.DRAFT_SUBMITTED);
        return toResponse(saved);
    }

    public RecipeDraftResponseDTO updateDraft(Integer draftId, RecipeDraftRequestDTO dto) {
        User currentUser = getCurrentUserOrThrow();
        RecipeDraft draft = findDraftOrThrow(draftId);
        assertOwnership(draft, currentUser);

        if (!draft.isEditable()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_DRAFT_NOT_EDITABLE));
        }

        validateProducts(dto.getComponents());

        boolean wasRejected = draft.getStatus() == RecipeDraftStatus.REJECTED;
        draft.setName(dto.getName());
        draft.setElaboration(dto.getElaboration());
        draft.setPresentation(dto.getPresentation());
        draft.setPortions(dto.getPortions());
        draft.setHidden(dto.isHidden());
        draft.setComponentsJson(writeJson(dto.getComponents()));
        draft.setAllergenIdsJson(writeJson(dto.getAllergenIds()));

        if (wasRejected) {
            draft.setStatus(RecipeDraftStatus.PENDING);
            draft.setRejectionReason(null);
            draft.setReviewedBy(null);
            draft.setReviewedAt(null);
            draft.setApprovedRecipeId(null);
        }

        RecipeDraft saved = recipeDraftRepository.save(draft);
        if (wasRejected) {
            notifyAdmins(saved, currentUser, MessageKey.NOTIFICATION_RECIPE_DRAFT_RESUBMITTED, NotificationType.DRAFT_RESUBMITTED);
        }
        return toResponse(saved);
    }

    public void deleteDraft(Integer draftId) {
        User currentUser = getCurrentUserOrThrow();
        RecipeDraft draft = findDraftOrThrow(draftId);
        assertOwnership(draft, currentUser);

        if (!draft.isEditable()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_DRAFT_NOT_EDITABLE));
        }

        recipeDraftRepository.delete(draft);
    }

    @Caching(evict = {
            @CacheEvict(value = "recipes_page", allEntries = true),
            @CacheEvict(value = "recipe_stats", allEntries = true),
            @CacheEvict(value = "weekly_plan_requirements", allEntries = true)
    })
    public RecipeDraftResponseDTO approveDraft(Integer draftId) {
        User currentUser = getCurrentUserOrThrow();
        RecipeDraft draft = findDraftOrThrow(draftId);

        if (draft.getStatus() != RecipeDraftStatus.PENDING) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_DRAFT_NOT_PENDING));
        }

        RecipeRequestDTO recipeRequest = recipeDraftMapper.toRecipeRequestDTO(draft, objectMapper);
        RecipeResponseDTO createdRecipe = recipeService.save(recipeRequest);
        draft.setStatus(RecipeDraftStatus.APPROVED);
        draft.setReviewedBy(currentUser);
        draft.setReviewedAt(LocalDateTime.now());
        draft.setApprovedRecipeId(createdRecipe.getId());
        draft.setRejectionReason(null);

        RecipeDraft saved = recipeDraftRepository.save(draft);
        notifyUser(saved, MessageKey.NOTIFICATION_RECIPE_DRAFT_APPROVED, NotificationType.DRAFT_APPROVED,
                saved.getCreatedBy(), currentUser);
        return toResponse(saved);
    }

    public RecipeDraftResponseDTO rejectDraft(Integer draftId, RecipeDraftRejectRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        RecipeDraft draft = findDraftOrThrow(draftId);

        if (draft.getStatus() != RecipeDraftStatus.PENDING) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_DRAFT_NOT_PENDING));
        }

        draft.setStatus(RecipeDraftStatus.REJECTED);
        draft.setRejectionReason(request.getReason());
        draft.setReviewedBy(currentUser);
        draft.setReviewedAt(LocalDateTime.now());

        RecipeDraft saved = recipeDraftRepository.save(draft);
        notifyUser(saved, MessageKey.NOTIFICATION_RECIPE_DRAFT_REJECTED, NotificationType.DRAFT_REJECTED,
                saved.getCreatedBy(), currentUser);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<RecipeDraftResponseDTO> findAll(Pageable pageable) {
        return recipeDraftRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<RecipeDraftResponseDTO> findByStatus(RecipeDraftStatus status, Pageable pageable) {
        return recipeDraftRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<RecipeDraftResponseDTO> findMyDrafts(Pageable pageable) {
        User currentUser = getCurrentUserOrThrow();
        return recipeDraftRepository.findByCreatedById(currentUser.getId(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RecipeDraftResponseDTO findById(Integer id) {
        User currentUser = getCurrentUserOrThrow();
        RecipeDraft draft = findDraftOrThrow(id);
        if (currentUser.getRole() == Role.USER && !draft.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_DRAFT_NOT_OWNER));
        }
        return toResponse(draft);
    }

    private RecipeDraftResponseDTO toResponse(RecipeDraft draft) {
        RecipeDraftResponseDTO response = recipeDraftMapper.toResponseDTO(draft);
        response.setComponents(recipeDraftMapper.readComponents(draft.getComponentsJson(), objectMapper));
        response.setAllergenIds(recipeDraftMapper.readAllergenIds(draft.getAllergenIdsJson(), objectMapper));
        response.setCreatedById(draft.getCreatedBy() != null ? draft.getCreatedBy().getId() : null);
        response.setCreatedByName(draft.getCreatedBy() != null ? draft.getCreatedBy().getName() : null);
        response.setReviewedByName(draft.getReviewedBy() != null ? draft.getReviewedBy().getName() : null);
        return response;
    }

    private RecipeDraft findDraftOrThrow(Integer id) {
        return recipeDraftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_RECIPE_DRAFT_NOT_FOUND)));
    }

    private User getCurrentUserOrThrow() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND));
        }
        return currentUser;
    }

    private void assertOwnership(RecipeDraft draft, User currentUser) {
        if (draft.getCreatedBy() == null || !draft.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_DRAFT_NOT_OWNER));
        }
    }

    private void validateProducts(List<RecipeComponentRequestDTO> components) {
        if (components == null || components.isEmpty()) {
            return;
        }

        Set<Integer> productIds = components.stream()
                .map(RecipeComponentRequestDTO::getProductId)
                .collect(Collectors.toSet());

        Map<Integer, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        if (productsById.size() != productIds.size()) {
            Set<Integer> missing = new HashSet<>(productIds);
            missing.removeAll(productsById.keySet());
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND,
                    new Object[]{missing.iterator().next()}));
        }
    }

    private String writeJson(Object value) {
        try {
            Object normalized = value == null ? List.of() : value;
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INTERNAL_SERVER_ERROR));
        }
    }

    private void notifyAdmins(RecipeDraft draft, User actor, MessageKey key, NotificationType type) {
        List<User> admins = userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN);
        if (admins.isEmpty()) {
            return;
        }

        String message = i18nService.getMessage(key, actor.getName(), draft.getName());
        persistentNotificationService.notifyUsersOfType(type, draft.getName(), message, draft.getId().longValue(), admins);
    }

    private void notifyUser(RecipeDraft draft, MessageKey key, NotificationType type, User recipient, User actor) {
        if (recipient == null) {
            return;
        }

        String message;
        if (key == MessageKey.NOTIFICATION_RECIPE_DRAFT_APPROVED) {
            message = i18nService.getMessage(key, draft.getName());
        } else if (key == MessageKey.NOTIFICATION_RECIPE_DRAFT_REJECTED) {
            message = i18nService.getMessage(key, draft.getName(), draft.getRejectionReason());
        } else {
            message = i18nService.getMessage(key, draft.getName(), actor.getName());
        }

        persistentNotificationService.createNotification(
                recipient,
                actor,
                type,
                draft.getName(),
                message,
                draft.getId().longValue(),
                "recipe-draft-" + draft.getId());
    }
}