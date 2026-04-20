package com.economato.inventory.application.usecase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.projection.IncidentChatMessageCountProjection;
import com.economato.inventory.application.dto.request.AttachAuditRequestDTO;
import com.economato.inventory.application.dto.request.CloseIncidentRequestDTO;
import com.economato.inventory.application.dto.request.CreateIncidentRequestDTO;
import com.economato.inventory.application.dto.request.OpenIncidentRequestDTO;
import com.economato.inventory.application.dto.request.RevertAuditFromIncidentRequestDTO;
import com.economato.inventory.application.dto.response.IncidentAuditAttachmentResponseDTO;
import com.economato.inventory.application.dto.response.IncidentListResponseDTO;
import com.economato.inventory.application.dto.response.IncidentResponseDTO;
import com.economato.inventory.application.dto.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.application.mapper.IncidentMapper;
import com.economato.inventory.application.mapper.RecipeCookingAuditMapper;
import com.economato.inventory.domain.model.Incident;
import com.economato.inventory.domain.model.IncidentAuditAttachment;
import com.economato.inventory.domain.model.IncidentSeverity;
import com.economato.inventory.domain.model.IncidentStatus;
import com.economato.inventory.domain.model.IncidentType;
import com.economato.inventory.domain.model.RecipeCookingAudit;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentAuditAttachmentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentTypeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.specification.IncidentSpecifications;
import com.economato.inventory.infrastructure.aspect.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class IncidentService {

    private static final int MAX_ADMIN_ATTACHABLE_AUDITS = 200;

    private final IncidentRepository incidentRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final IncidentAuditAttachmentRepository incidentAuditAttachmentRepository;
    private final IncidentChatMessageRepository incidentChatMessageRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final IncidentMapper incidentMapper;
    private final RecipeCookingAuditMapper recipeCookingAuditMapper;
    private final SecurityContextHelper securityContextHelper;
    private final IncidentParticipantService incidentParticipantService;
    private final PersistentNotificationService persistentNotificationService;
    private final RecipeService recipeService;
    private final I18nService i18nService;
    private final SystemConfigService systemConfigService;

    @Autowired
    public IncidentService(IncidentRepository incidentRepository,
            IncidentTypeRepository incidentTypeRepository,
            IncidentAuditAttachmentRepository incidentAuditAttachmentRepository,
            IncidentChatMessageRepository incidentChatMessageRepository,
            RecipeCookingAuditRepository recipeCookingAuditRepository,
            IncidentMapper incidentMapper,
            RecipeCookingAuditMapper recipeCookingAuditMapper,
            SecurityContextHelper securityContextHelper,
            IncidentParticipantService incidentParticipantService,
            PersistentNotificationService persistentNotificationService,
            RecipeService recipeService,
            I18nService i18nService,
            @Autowired(required = false) SystemConfigService systemConfigService) {
        this.incidentRepository = incidentRepository;
        this.incidentTypeRepository = incidentTypeRepository;
        this.incidentAuditAttachmentRepository = incidentAuditAttachmentRepository;
        this.incidentChatMessageRepository = incidentChatMessageRepository;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
        this.incidentMapper = incidentMapper;
        this.recipeCookingAuditMapper = recipeCookingAuditMapper;
        this.securityContextHelper = securityContextHelper;
        this.incidentParticipantService = incidentParticipantService;
        this.persistentNotificationService = persistentNotificationService;
        this.recipeService = recipeService;
        this.i18nService = i18nService;
        this.systemConfigService = systemConfigService;
    }

    public IncidentService(IncidentRepository incidentRepository,
            IncidentTypeRepository incidentTypeRepository,
            IncidentAuditAttachmentRepository incidentAuditAttachmentRepository,
            IncidentChatMessageRepository incidentChatMessageRepository,
            RecipeCookingAuditRepository recipeCookingAuditRepository,
            IncidentMapper incidentMapper,
            RecipeCookingAuditMapper recipeCookingAuditMapper,
            SecurityContextHelper securityContextHelper,
            IncidentParticipantService incidentParticipantService,
            PersistentNotificationService persistentNotificationService,
            RecipeService recipeService,
            I18nService i18nService) {
        this(incidentRepository, incidentTypeRepository, incidentAuditAttachmentRepository, incidentChatMessageRepository,
                recipeCookingAuditRepository, incidentMapper, recipeCookingAuditMapper, securityContextHelper,
                incidentParticipantService, persistentNotificationService, recipeService, i18nService, null);
    }

    public IncidentResponseDTO createIncident(CreateIncidentRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        ensureCreatorRole(currentUser);

        IncidentType incidentType = incidentTypeRepository.findById(request.getIncidentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_TYPE_NOT_FOUND)));

        if (!incidentType.isActive()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_INVALID_STATE));
        }

        Incident incident = Incident.builder()
                .incidentType(incidentType)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .status(IncidentStatus.CREADO)
                .createdBy(currentUser)
                .relatedTeacher(currentUser.getRole() == Role.ELEVATED ? currentUser.getTeacher() : null)
                .build();

        Incident saved = incidentRepository.save(incident);

        if (request.getCookingAuditIds() != null && !request.getCookingAuditIds().isEmpty()) {
            attachAuditsInternal(saved, request.getCookingAuditIds(), currentUser);
        }

        persistentNotificationService.notifyIncidentCreated(saved);
        return getById(saved.getId());
    }

    @Transactional(readOnly = true)
    public Page<IncidentListResponseDTO> listIncidents(IncidentStatus status,
                                                       IncidentSeverity severity,
                                                       Integer incidentTypeId,
                                                       Integer createdById,
                                                       LocalDateTime from,
                                                       LocalDateTime to,
                                                       Pageable pageable) {
        User currentUser = getCurrentUserOrThrow();
        Pageable normalized = normalizeSort(pageable);

        Specification<Incident> specification = Specification
                .where(IncidentSpecifications.hasStatus(status))
                .and(IncidentSpecifications.hasSeverity(severity))
                .and(IncidentSpecifications.hasIncidentTypeId(incidentTypeId))
                .and(IncidentSpecifications.hasCreatedById(createdById))
                .and(IncidentSpecifications.createdAfter(from))
                .and(IncidentSpecifications.createdBefore(to));

        if (currentUser.getRole() == Role.ELEVATED) {
            specification = specification.and(IncidentSpecifications.belongsToUser(currentUser.getId()));
        } else if (currentUser.getRole() == Role.CHEF) {
            specification = specification.and(IncidentSpecifications.belongsToUserOrTeacher(currentUser.getId(), currentUser.getId()));
        }

        Page<Incident> incidentPage = incidentRepository.findAll(specification, normalized);
        Map<Long, Long> chatCountByIncidentId = resolveChatCountByIncidentId(incidentPage.getContent());

        List<IncidentListResponseDTO> content = incidentPage.getContent()
            .stream()
            .map(incident -> incidentMapper.toListResponseDTO(
                incident,
                chatCountByIncidentId.getOrDefault(incident.getId(), 0L)))
            .toList();

        return new RestPage<>(content, incidentPage.getPageable(), incidentPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public IncidentResponseDTO getById(Long incidentId) {
        User currentUser = getCurrentUserOrThrow();
        Incident incident = getIncidentOrThrow(incidentId);
        ensureParticipant(incident, currentUser);

        List<IncidentAuditAttachment> attachments = incidentAuditAttachmentRepository.findByIncidentId(incidentId);
        long chatCount = incidentChatMessageRepository.countByIncidentId(incidentId);
        return incidentMapper.toResponseDTO(incident, attachments, chatCount);
    }

    public IncidentResponseDTO openIncident(Long incidentId, OpenIncidentRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        ensureAdmin(currentUser);

        Incident incident = getIncidentOrThrow(incidentId);
        if (incident.getStatus() != IncidentStatus.CREADO) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_INVALID_STATE));
        }

        incident.setStatus(IncidentStatus.ABIERTO);
        incident.setSeverity(request.getSeverity());
        incident.setOpenedAt(LocalDateTime.now());
        incident.setOpenedBy(currentUser);

        Incident saved = incidentRepository.save(incident);
        persistentNotificationService.notifyIncidentOpened(saved);
        return getById(saved.getId());
    }

    public IncidentResponseDTO closeIncident(Long incidentId, CloseIncidentRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        ensureAdmin(currentUser);

        Incident incident = getIncidentOrThrow(incidentId);
        if (incident.getStatus() != IncidentStatus.ABIERTO) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_INVALID_STATE));
        }

        if (request.isHasResolution() && (request.getResolution() == null || request.getResolution().isBlank())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_INVALID_STATE));
        }

        incident.setStatus(request.isHasResolution() ? IncidentStatus.CERRADO_CON_RESOLUCION : IncidentStatus.CERRADO_SIN_RESOLUCION);
        incident.setResolution(request.isHasResolution() ? request.getResolution().trim() : null);
        incident.setClosedAt(LocalDateTime.now());
        incident.setClosedBy(currentUser);

        Incident saved = incidentRepository.save(incident);
        persistentNotificationService.notifyIncidentClosed(saved);
        return getById(saved.getId());
    }

    public List<IncidentAuditAttachmentResponseDTO> attachAudits(Long incidentId, AttachAuditRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        Incident incident = getIncidentOrThrow(incidentId);
        ensureParticipant(incident, currentUser);

        if (isClosed(incident)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_INVALID_STATE));
        }

        attachAuditsInternal(incident, request.getCookingAuditIds(), currentUser);

        return incidentAuditAttachmentRepository.findByIncidentId(incidentId)
                .stream()
                .map(incidentMapper::toAttachmentResponseDTO)
                .toList();
    }

    @RealtimeSync(entityType = "incident", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"incident"})
    public IncidentAuditAttachmentResponseDTO revertAuditFromIncident(Long incidentId,
                                                                      Long attachmentId,
                                                                      RevertAuditFromIncidentRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        ensureAdmin(currentUser);

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
    public List<RecipeCookingAuditResponseDTO> getAttachableAudits(Long incidentId) {
        User currentUser = getCurrentUserOrThrow();
        Incident incident = getIncidentOrThrow(incidentId);
        ensureParticipant(incident, currentUser);

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

    private void attachAuditsInternal(Incident incident, List<Long> cookingAuditIds, User currentUser) {
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

    private void ensureCreatorRole(User user) {
        if (user == null || (user.getRole() != Role.ADMIN && user.getRole() != Role.CHEF && user.getRole() != Role.ELEVATED)) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
        }
    }

    private void ensureAdmin(User user) {
        if (user == null || user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
        }
    }

    private void ensureParticipant(Incident incident, User currentUser) {
        if (!incidentParticipantService.isParticipant(incident, currentUser)) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_NOT_PARTICIPANT));
        }
    }

    private Incident getIncidentOrThrow(Long incidentId) {
        return incidentRepository.findDetailById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_NOT_FOUND)));
    }

    private Map<Long, Long> resolveChatCountByIncidentId(List<Incident> incidents) {
        if (incidents == null || incidents.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> incidentIds = incidents.stream()
                .map(Incident::getId)
                .toList();

        return incidentChatMessageRepository.countByIncidentIds(incidentIds)
                .stream()
                .collect(Collectors.toMap(
                        IncidentChatMessageCountProjection::getIncidentId,
                        projection -> projection.getMessageCount() == null ? 0L : projection.getMessageCount(),
                        (left, right) -> left));
    }

    private User getCurrentUserOrThrow() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
        }
        return currentUser;
    }

    private Pageable normalizeSort(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, defaultIncidentSort());
        }
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultIncidentSort());
        }
        return pageable;
    }

    private Sort defaultIncidentSort() {
        return JpaSort.unsafe(Sort.Direction.DESC,
                        "CASE severity WHEN 'ALTA' THEN 3 WHEN 'MEDIA' THEN 2 WHEN 'BAJA' THEN 1 ELSE 0 END")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
