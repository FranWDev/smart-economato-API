package com.economato.inventory.application.usecase.incident;

import com.economato.inventory.application.dto.incident.request.RevertAuditFromIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.response.IncidentAuditAttachmentResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.application.dto.shared.request.AttachAuditRequestDTO;
import com.economato.inventory.application.mapper.incident.IncidentMapper;
import com.economato.inventory.application.mapper.recipe.RecipeCookingAuditMapper;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.incident.IncidentAuditAttachment;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import com.economato.inventory.domain.model.recipe.RecipeCookingAudit;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentAuditAttachmentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class IncidentAttachmentService {

    private static final int MAX_ADMIN_ATTACHABLE_AUDITS = 200;

    private final IncidentAuditAttachmentRepository incidentAuditAttachmentRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final IncidentMapper incidentMapper;
    private final RecipeCookingAuditMapper recipeCookingAuditMapper;
    private final SecurityContextHelper securityContextHelper;
    private final IncidentParticipantService incidentParticipantService;
    private final RecipeService recipeService;
    private final I18nService i18nService;
    private final SystemConfigService systemConfigService;

    public IncidentAttachmentService(IncidentAuditAttachmentRepository incidentAuditAttachmentRepository,
                                     RecipeCookingAuditRepository recipeCookingAuditRepository,
                                     IncidentMapper incidentMapper,
                                     RecipeCookingAuditMapper recipeCookingAuditMapper,
                                     SecurityContextHelper securityContextHelper,
                                     IncidentParticipantService incidentParticipantService,
                                     RecipeService recipeService,
                                     I18nService i18nService,
                                     SystemConfigService systemConfigService) {
        this.incidentAuditAttachmentRepository = incidentAuditAttachmentRepository;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
        this.incidentMapper = incidentMapper;
        this.recipeCookingAuditMapper = recipeCookingAuditMapper;
        this.securityContextHelper = securityContextHelper;
        this.incidentParticipantService = incidentParticipantService;
        this.recipeService = recipeService;
        this.i18nService = i18nService;
        this.systemConfigService = systemConfigService;
    }

    public List<IncidentAuditAttachmentResponseDTO> attachAudits(Long incidentId, Incident incident, AttachAuditRequestDTO request, User currentUser) {
        if (isClosed(incident)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_INVALID_STATE));
        }

        attachAuditsInternal(incident, request.getCookingAuditIds(), currentUser);

        return incidentAuditAttachmentRepository.findByIncidentId(incidentId)
                .stream()
                .map(incidentMapper::toAttachmentResponseDTO)
                .toList();
    }

    @RealtimeSync(entityType = "incident", action = "UPDATE", idFromArg = 0, affectedDomains = {"incident"})
    public IncidentAuditAttachmentResponseDTO revertAuditFromIncident(Long incidentId, Long attachmentId, RevertAuditFromIncidentRequestDTO request, User currentUser) {
        IncidentAuditAttachment attachment = incidentAuditAttachmentRepository.findByIdWithDetails(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        if (!attachment.getIncident().getId().equals(incidentId)) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
        }

        if (attachment.isReverted()) {
            return incidentMapper.toAttachmentResponseDTO(attachment);
        }

        String reason = request.getReason();
        recipeService.revertCooking(attachment.getCookingAudit().getId(), reason);

        attachment.setReverted(true);
        attachment.setRevertedAt(LocalDateTime.now());
        attachment.setRevertedBy(currentUser);

        return incidentMapper.toAttachmentResponseDTO(incidentAuditAttachmentRepository.save(attachment));
    }

    @Transactional(readOnly = true)
    public List<RecipeCookingAuditResponseDTO> getAttachableAudits(Incident incident, User currentUser) {
        List<RecipeCookingAudit> audits;
        if (currentUser.getRole() == Role.ADMIN) {
            audits = recipeCookingAuditRepository
                    .findAllOrderByDateDesc(PageRequest.of(0, maxAdminAttachableAudits()))
                    .getContent();
        } else {
            Set<Integer> allowedUserIds = incidentParticipantService.allowedAuditUserIds(currentUser);
            audits = recipeCookingAuditRepository.findByUserIdInOrderByCookingDateDesc(new ArrayList<>(allowedUserIds));
        }

        return audits.stream().map(recipeCookingAuditMapper::toResponseDTO).toList();
    }

    public void attachAuditsInternal(Incident incident, List<Long> cookingAuditIds, User currentUser) {
        if (cookingAuditIds == null || cookingAuditIds.isEmpty()) {
            return;
        }

        List<Long> uniqueCookingAuditIds = new ArrayList<>(new HashSet<>(cookingAuditIds));
        List<RecipeCookingAudit> audits = recipeCookingAuditRepository.findAllByIdWithUser(uniqueCookingAuditIds);
        if (audits.size() != uniqueCookingAuditIds.size()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_AUDIT_NOT_ATTACHABLE));
        }

        List<Long> alreadyAttachedAuditIds = incidentAuditAttachmentRepository
                .findAttachedCookingAuditIds(incident.getId(), uniqueCookingAuditIds);
        if (!alreadyAttachedAuditIds.isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_AUDIT_ALREADY_ATTACHED));
        }

        Set<Integer> allowedUserIds = currentUser.getRole() == Role.ADMIN
                ? Collections.emptySet()
                : incidentParticipantService.allowedAuditUserIds(currentUser);

        List<IncidentAuditAttachment> attachmentsToPersist = new ArrayList<>(audits.size());

        for (RecipeCookingAudit audit : audits) {
            if (currentUser.getRole() != Role.ADMIN) {
                Integer ownerId = audit.getUser() != null ? audit.getUser().getId() : null;
                if (ownerId == null || !allowedUserIds.contains(ownerId)) {
                    throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_AUDIT_NOT_ATTACHABLE));
                }
            }

            IncidentAuditAttachment attachment = IncidentAuditAttachment.builder()
                    .incident(incident)
                    .cookingAudit(audit)
                    .attachedAt(LocalDateTime.now())
                    .attachedBy(currentUser)
                    .reverted(false)
                    .build();
            attachmentsToPersist.add(attachment);
        }

        if (!attachmentsToPersist.isEmpty()) {
            incidentAuditAttachmentRepository.saveAll(attachmentsToPersist);
        }
    }

    private boolean isClosed(Incident incident) {
        return incident.getStatus() == IncidentStatus.CERRADO_CON_RESOLUCION
                || incident.getStatus() == IncidentStatus.CERRADO_SIN_RESOLUCION;
    }

    private int maxAdminAttachableAudits() {
        if (systemConfigService == null) {
            return MAX_ADMIN_ATTACHABLE_AUDITS;
        }
        try {
            return systemConfigService.getMaxAdminAttachableAudits();
        } catch (Exception ignored) {
            return MAX_ADMIN_ATTACHABLE_AUDITS;
        }
    }
}
