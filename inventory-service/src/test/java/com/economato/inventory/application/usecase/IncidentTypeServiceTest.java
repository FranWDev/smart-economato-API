package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.IncidentTypeRequestDTO;
import com.economato.inventory.application.dto.response.IncidentTypeResponseDTO;
import com.economato.inventory.application.mapper.IncidentTypeMapper;
import com.economato.inventory.domain.model.IncidentType;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentTypeServiceTest {

    @Mock
    private IncidentTypeRepository incidentTypeRepository;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private I18nService i18nService;

    private IncidentTypeService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new IncidentTypeService(
                incidentTypeRepository,
                new IncidentTypeMapper(),
                securityContextHelper,
                i18nService
        );

        admin = new User();
        admin.setId(1);
        admin.setRole(Role.ADMIN);

        lenient().when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).getKey());
    }

    @Test
    void createType_WithValidData_ShouldSaveAndReturn() {
        IncidentTypeRequestDTO request = IncidentTypeRequestDTO.builder()
                .name(" Food Safety ")
                .description("Food safety incident")
                .build();

        IncidentType saved = IncidentType.builder()
                .id(11)
                .name("Food Safety")
                .description("Food safety incident")
                .isActive(true)
                .build();

        when(incidentTypeRepository.findByNameIgnoreCase("Food Safety")).thenReturn(Optional.empty());
        when(incidentTypeRepository.save(any(IncidentType.class))).thenReturn(saved);

        IncidentTypeResponseDTO result = service.create(request);

        assertNotNull(result);
        assertEquals(11, result.getId());
        assertEquals("Food Safety", result.getName());
        assertTrue(result.isActive());
        verify(incidentTypeRepository).save(any(IncidentType.class));
    }

    @Test
    void createType_WithDuplicateName_ShouldThrowInvalidOperation() {
        IncidentTypeRequestDTO request = IncidentTypeRequestDTO.builder()
                .name("contaminacion")
                .description("desc")
                .build();

        IncidentType existing = IncidentType.builder().id(7).name("CONTAMINACION").isActive(true).build();
        when(incidentTypeRepository.findByNameIgnoreCase("contaminacion")).thenReturn(Optional.of(existing));

        assertThrows(InvalidOperationException.class, () -> service.create(request));

        verify(incidentTypeRepository, never()).save(any(IncidentType.class));
    }

    @Test
    void getAllActiveTypes_ShouldReturnOnlyActive() {
        IncidentType activeA = IncidentType.builder().id(1).name("A").isActive(true).build();
        IncidentType activeB = IncidentType.builder().id(2).name("B").isActive(true).build();
        when(incidentTypeRepository.findByIsActiveTrue()).thenReturn(List.of(activeA, activeB));

        List<IncidentTypeResponseDTO> result = service.getActiveTypes();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(IncidentTypeResponseDTO::isActive));
        verify(incidentTypeRepository).findByIsActiveTrue();
    }

    @Test
    void toggleActive_WhenTypeExists_ShouldFlipIsActive() {
        IncidentType type = IncidentType.builder().id(5).name("Toggle").isActive(true).build();
        when(incidentTypeRepository.findById(5)).thenReturn(Optional.of(type));
        when(incidentTypeRepository.save(any(IncidentType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentTypeResponseDTO result = service.toggleActive(5);

        assertNotNull(result);
        assertFalse(result.isActive());
        verify(incidentTypeRepository).save(type);
    }

    @Test
    void updateType_WhenTypeExists_ShouldUpdateFields() {
        IncidentTypeRequestDTO request = IncidentTypeRequestDTO.builder()
                .name("New Name")
                .description("New Description")
                .build();

        IncidentType existing = IncidentType.builder().id(9).name("Old").description("Old").isActive(true).build();
        when(incidentTypeRepository.findById(9)).thenReturn(Optional.of(existing));
        when(incidentTypeRepository.findByNameIgnoreCase("New Name")).thenReturn(Optional.empty());
        when(incidentTypeRepository.save(any(IncidentType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentTypeResponseDTO result = service.update(9, request);

        assertEquals("New Name", result.getName());
        assertEquals("New Description", result.getDescription());
        verify(incidentTypeRepository).save(existing);
    }

    @Test
    void createIncidentWithInactiveType_ShouldBeRejected() {
        IncidentType inactiveType = IncidentType.builder().id(3).name("Inactive").isActive(false).build();
        when(incidentTypeRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(incidentTypeRepository.save(any(IncidentType.class))).thenReturn(inactiveType);

        IncidentTypeResponseDTO result = service.create(
                IncidentTypeRequestDTO.builder().name("Inactive").description("d").build());

        assertNotNull(result);
        assertFalse(result.isActive());
    }
}
