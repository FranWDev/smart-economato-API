package com.economato.inventory.application.usecase.incident;

import com.economato.inventory.application.dto.incident.request.IncidentTypeRequestDTO;
import com.economato.inventory.application.dto.incident.response.IncidentTypeResponseDTO;
import com.economato.inventory.application.mapper.incident.IncidentTypeMapper;
import com.economato.inventory.domain.model.incident.IncidentType;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentTypeRepository;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class IncidentTypeService {

    private final IncidentTypeRepository incidentTypeRepository;
    private final IncidentTypeMapper incidentTypeMapper;
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;

    @Transactional(readOnly = true)
    public List<IncidentTypeResponseDTO> getActiveTypes() {
        return incidentTypeRepository.findByIsActiveTrue().stream()
                .map(incidentTypeMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentTypeResponseDTO> getAllTypes() {
        ensureAdmin();
        return incidentTypeRepository.findAll().stream()
                .map(incidentTypeMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentTypeResponseDTO getById(Integer id) {
        IncidentType incidentType = incidentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_TYPE_NOT_FOUND)));
        return incidentTypeMapper.toResponseDTO(incidentType);
    }

    public IncidentTypeResponseDTO create(IncidentTypeRequestDTO request) {
        ensureAdmin();
        validateUniqueName(request.getName(), null);

        IncidentType entity = IncidentType.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .isActive(true)
                .build();

        return incidentTypeMapper.toResponseDTO(incidentTypeRepository.save(entity));
    }

    public IncidentTypeResponseDTO update(Integer id, IncidentTypeRequestDTO request) {
        ensureAdmin();
        IncidentType incidentType = incidentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_TYPE_NOT_FOUND)));

        validateUniqueName(request.getName(), id);

        incidentType.setName(request.getName().trim());
        incidentType.setDescription(request.getDescription());

        return incidentTypeMapper.toResponseDTO(incidentTypeRepository.save(incidentType));
    }

    public IncidentTypeResponseDTO toggleActive(Integer id) {
        ensureAdmin();
        IncidentType incidentType = incidentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_TYPE_NOT_FOUND)));

        incidentType.setActive(!incidentType.isActive());
        return incidentTypeMapper.toResponseDTO(incidentTypeRepository.save(incidentType));
    }

    private void validateUniqueName(String name, Integer currentId) {
        incidentTypeRepository.findByNameIgnoreCase(name.trim())
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_TYPE_ALREADY_EXISTS));
                });
    }

    private void ensureAdmin() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
        }
    }
}
