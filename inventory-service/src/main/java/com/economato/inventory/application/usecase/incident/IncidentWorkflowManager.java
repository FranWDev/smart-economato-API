package com.economato.inventory.application.usecase.incident;

import java.time.LocalDateTime;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.incident.request.CloseIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.CreateIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.OpenIncidentRequestDTO;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import com.economato.inventory.domain.model.incident.IncidentType;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentTypeRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

@Service
@Transactional(rollbackFor = Exception.class)
public class IncidentWorkflowManager {

    private final IncidentRepository incidentRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final PersistentNotificationService persistentNotificationService;
    private final IncidentParticipantService incidentParticipantService;
    private final IncidentAttachmentService incidentAttachmentService;
    private final I18nService i18nService;

    public IncidentWorkflowManager(IncidentRepository incidentRepository,
                                   IncidentTypeRepository incidentTypeRepository,
                                   PersistentNotificationService persistentNotificationService,
                                   IncidentParticipantService incidentParticipantService,
                                   IncidentAttachmentService incidentAttachmentService,
                                   I18nService i18nService) {
        this.incidentRepository = incidentRepository;
        this.incidentTypeRepository = incidentTypeRepository;
        this.persistentNotificationService = persistentNotificationService;
        this.incidentParticipantService = incidentParticipantService;
        this.incidentAttachmentService = incidentAttachmentService;
        this.i18nService = i18nService;
    }

    public Incident createIncident(CreateIncidentRequestDTO request, User currentUser) {
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
            incidentAttachmentService.attachAuditsInternal(saved, request.getCookingAuditIds(), currentUser);
        }

        persistentNotificationService.notifyIncidentCreated(saved);
        return saved;
    }

    public Incident openIncident(Incident incident, OpenIncidentRequestDTO request, User currentUser) {
        ensureAdmin(currentUser);

        if (incident.getStatus() != IncidentStatus.CREADO) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_INVALID_STATE));
        }

        incident.setStatus(IncidentStatus.ABIERTO);
        incident.setSeverity(request.getSeverity());
        incident.setOpenedAt(LocalDateTime.now());
        incident.setOpenedBy(currentUser);

        Incident saved = incidentRepository.save(incident);
        persistentNotificationService.notifyIncidentOpened(saved);
        return saved;
    }

    public Incident closeIncident(Incident incident, CloseIncidentRequestDTO request, User currentUser) {
        ensureAdmin(currentUser);

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
        return saved;
    }

    public void ensureCreatorRole(User user) {
        if (user == null || (user.getRole() != Role.ADMIN && user.getRole() != Role.CHEF && user.getRole() != Role.ELEVATED)) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
        }
    }

    public void ensureAdmin(User user) {
        if (user == null || user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
        }
    }

    public void ensureParticipant(Incident incident, User currentUser) {
        if (!incidentParticipantService.isParticipant(incident, currentUser)) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_NOT_PARTICIPANT));
        }
    }
}
