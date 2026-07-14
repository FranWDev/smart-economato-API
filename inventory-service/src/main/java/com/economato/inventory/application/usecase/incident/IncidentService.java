package com.economato.inventory.application.usecase.incident;

import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.incident.projection.IncidentChatMessageCountProjection;
import com.economato.inventory.application.dto.shared.request.AttachAuditRequestDTO;
import com.economato.inventory.application.dto.incident.request.CloseIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.CreateIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.OpenIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.RevertAuditFromIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.response.IncidentAuditAttachmentResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentListResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentChatMessageResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.application.mapper.incident.IncidentMapper;
import com.economato.inventory.application.mapper.recipe.RecipeCookingAuditMapper;
import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.incident.IncidentAuditAttachment;
import com.economato.inventory.domain.model.incident.IncidentSeverity;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentAuditAttachmentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentTypeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.specification.incident.IncidentSpecifications;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.adapter.out.external.incident.reports.IncidentReportPdfService;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentAuditAttachmentRepository incidentAuditAttachmentRepository;
    private final IncidentChatMessageRepository incidentChatMessageRepository;
    private final IncidentMapper incidentMapper;
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;
    private final IncidentWorkflowManager incidentWorkflowManager;
    private final IncidentAttachmentService incidentAttachmentService;
    private final IncidentChatService incidentChatService;
    private final IncidentReportPdfService incidentReportPdfService;

    public IncidentResponseDTO createIncident(CreateIncidentRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        Incident incident = incidentWorkflowManager.createIncident(request, currentUser);
        return getById(incident.getId());
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
        incidentWorkflowManager.ensureParticipant(incident, currentUser);

        List<IncidentAuditAttachment> attachments = incidentAuditAttachmentRepository.findByIncidentId(incidentId);
        long chatCount = incidentChatMessageRepository.countByIncidentId(incidentId);
        return incidentMapper.toResponseDTO(incident, attachments, chatCount);
    }

    public IncidentResponseDTO openIncident(Long incidentId, OpenIncidentRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        Incident incident = getIncidentOrThrow(incidentId);
        Incident opened = incidentWorkflowManager.openIncident(incident, request, currentUser);
        return getById(opened.getId());
    }

    public IncidentResponseDTO closeIncident(Long incidentId, CloseIncidentRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        Incident incident = getIncidentOrThrow(incidentId);
        Incident closed = incidentWorkflowManager.closeIncident(incident, request, currentUser);
        return getById(closed.getId());
    }

    public List<IncidentAuditAttachmentResponseDTO> attachAudits(Long incidentId, AttachAuditRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        Incident incident = getIncidentOrThrow(incidentId);
        incidentWorkflowManager.ensureParticipant(incident, currentUser);

        return incidentAttachmentService.attachAudits(incidentId, incident, request, currentUser);
    }

    public IncidentAuditAttachmentResponseDTO revertAuditFromIncident(Long incidentId,
                                                                      Long attachmentId,
                                                                      RevertAuditFromIncidentRequestDTO request) {
        User currentUser = getCurrentUserOrThrow();
        incidentWorkflowManager.ensureAdmin(currentUser);
        request.setAuditAttachmentId(attachmentId);
        return incidentAttachmentService.revertAuditFromIncident(incidentId, attachmentId, request, currentUser);
    }

    @Transactional(readOnly = true)
    public List<RecipeCookingAuditResponseDTO> getAttachableAudits(Long incidentId) {
        User currentUser = getCurrentUserOrThrow();
        Incident incident = getIncidentOrThrow(incidentId);
        incidentWorkflowManager.ensureParticipant(incident, currentUser);

        return incidentAttachmentService.getAttachableAudits(incident, currentUser);
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

    @Transactional(readOnly = true)
    public byte[] exportIncidentPdf(Long id) {
        IncidentResponseDTO incident = getById(id);
        List<IncidentChatMessageResponseDTO> chat = incidentChatService.getHistory(id, Pageable.unpaged()).getContent();
        return incidentReportPdfService.generateIncidentReport(incident, chat);
    }
}

