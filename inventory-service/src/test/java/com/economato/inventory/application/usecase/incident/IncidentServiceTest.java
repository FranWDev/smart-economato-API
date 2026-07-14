package com.economato.inventory.application.usecase.incident;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.application.usecase.incident.IncidentChatService;
import com.economato.inventory.infrastructure.adapter.out.external.incident.reports.IncidentReportPdfService;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import com.economato.inventory.application.dto.incident.projection.IncidentChatMessageCountProjection;
import com.economato.inventory.application.dto.shared.request.AttachAuditRequestDTO;
import com.economato.inventory.application.dto.incident.request.CloseIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.CreateIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.OpenIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.RevertAuditFromIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.response.IncidentAuditAttachmentResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentListResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentResponseDTO;
import com.economato.inventory.application.mapper.incident.IncidentMapper;
import com.economato.inventory.application.mapper.incident.IncidentTypeMapper;
import com.economato.inventory.application.mapper.recipe.RecipeCookingAuditMapper;
import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.incident.IncidentAuditAttachment;
import com.economato.inventory.domain.model.incident.IncidentSeverity;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import com.economato.inventory.domain.model.incident.IncidentType;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeCookingAudit;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentAuditAttachmentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentTypeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentTypeRepository incidentTypeRepository;
    @Mock
    private IncidentAuditAttachmentRepository incidentAuditAttachmentRepository;
    @Mock
    private IncidentChatMessageRepository incidentChatMessageRepository;
    @Mock
    private RecipeCookingAuditRepository recipeCookingAuditRepository;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private IncidentParticipantService incidentParticipantService;
    @Mock
    private PersistentNotificationService persistentNotificationService;
    @Mock
    private RecipeService recipeService;
    @Mock
    private I18nService i18nService;

    private IncidentService service;

    private User admin;
    private User chef;
    private User chefTwo;
    private User elevatedWithTeacher;
    private User elevatedWithoutTeacher;
    private User chefStudent;

    private IncidentType activeType;
    private IncidentType inactiveType;
    private RecipeCookingAudit auditByChef;
    private RecipeCookingAudit auditByStudent;

    @BeforeEach
    void setUp() {
        IncidentAttachmentService attachmentService = new IncidentAttachmentService(
                incidentAuditAttachmentRepository,
                recipeCookingAuditRepository,
                new IncidentMapper(new IncidentTypeMapper()),
                new RecipeCookingAuditMapper(),
                securityContextHelper,
                incidentParticipantService,
                recipeService,
                i18nService,
                null);
        IncidentWorkflowManager workflowManager = new IncidentWorkflowManager(
                incidentRepository,
                incidentTypeRepository,
                persistentNotificationService,
                incidentParticipantService,
                attachmentService,
                i18nService);
        service = new IncidentService(
                incidentRepository,
                incidentAuditAttachmentRepository,
                incidentChatMessageRepository,
                new IncidentMapper(new IncidentTypeMapper()),
                securityContextHelper,
                i18nService,
                workflowManager,
                attachmentService,
                mock(IncidentChatService.class),
                mock(IncidentReportPdfService.class)
        );

        admin = user(1, "Admin", "admin", Role.ADMIN, null);
        chef = user(2, "Chef One", "chef1", Role.CHEF, null);
        chefTwo = user(3, "Chef Two", "chef2", Role.CHEF, null);
        elevatedWithTeacher = user(4, "Elevated", "elev1", Role.ELEVATED, chef);
        elevatedWithoutTeacher = user(5, "Elevated NT", "elev2", Role.ELEVATED, null);
        chefStudent = user(6, "Student", "stud1", Role.USER, chef);

        activeType = IncidentType.builder().id(10).name("Type A").isActive(true).build();
        inactiveType = IncidentType.builder().id(11).name("Type Inactive").isActive(false).build();

        Recipe recipe = new Recipe();
        recipe.setId(50);
        recipe.setName("Rice");

        auditByChef = RecipeCookingAudit.builder()
                .id(100L)
                .recipe(recipe)
                .user(chef)
                .quantityCooked(new BigDecimal("3.0"))
                .cookingDate(LocalDateTime.now().minusHours(2))
                .build();

        auditByStudent = RecipeCookingAudit.builder()
                .id(101L)
                .recipe(recipe)
                .user(chefStudent)
                .quantityCooked(new BigDecimal("1.0"))
                .cookingDate(LocalDateTime.now().minusHours(1))
                .build();

        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).getKey());
        lenient().when(incidentAuditAttachmentRepository.findByIncidentId(anyLong())).thenReturn(List.of());
        lenient().when(incidentChatMessageRepository.countByIncidentId(anyLong())).thenReturn(0L);
        lenient().when(incidentParticipantService.isParticipant(any(Incident.class), any(User.class))).thenReturn(true);
    }

    @Test
    void create_AsChef_ShouldCreateWithStatusCreado() {
        when(securityContextHelper.getCurrentUser()).thenReturn(chef);
        AtomicReference<Incident> savedRef = new AtomicReference<>();
        when(incidentTypeRepository.findById(activeType.getId())).thenReturn(Optional.of(activeType));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setId(200L);
            incident.setCreatedAt(LocalDateTime.now());
            savedRef.set(incident);
            return incident;
        });
        when(incidentRepository.findDetailById(200L)).thenAnswer(invocation -> Optional.of(savedRef.get()));

        CreateIncidentRequestDTO request = CreateIncidentRequestDTO.builder()
                .incidentTypeId(activeType.getId())
                .title("Wrong cooked rice")
                .description("Description")
                .build();

        IncidentResponseDTO result = service.createIncident(request);

        assertEquals(IncidentStatus.CREADO, result.getStatus());
        assertNull(result.getRelatedTeacher());
        verify(persistentNotificationService).notifyIncidentCreated(any(Incident.class));
    }

    @Test
    void create_AsElevated_ShouldAutoAssignRelatedTeacher() {
        when(securityContextHelper.getCurrentUser()).thenReturn(elevatedWithTeacher);
        AtomicReference<Incident> savedRef = new AtomicReference<>();
        when(incidentTypeRepository.findById(activeType.getId())).thenReturn(Optional.of(activeType));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setId(201L);
            incident.setCreatedAt(LocalDateTime.now());
            savedRef.set(incident);
            return incident;
        });
        when(incidentRepository.findDetailById(201L)).thenAnswer(invocation -> Optional.of(savedRef.get()));

        IncidentResponseDTO result = service.createIncident(CreateIncidentRequestDTO.builder()
                .incidentTypeId(activeType.getId())
                .title("Incident")
                .description("Desc")
                .build());

        assertNotNull(result.getRelatedTeacher());
        assertEquals(chef.getId(), result.getRelatedTeacher().getId());
    }

    @Test
    void create_AsElevatedWithoutTeacher_ShouldCreateWithNullRelatedTeacher() {
        when(securityContextHelper.getCurrentUser()).thenReturn(elevatedWithoutTeacher);
        AtomicReference<Incident> savedRef = new AtomicReference<>();
        when(incidentTypeRepository.findById(activeType.getId())).thenReturn(Optional.of(activeType));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setId(202L);
            incident.setCreatedAt(LocalDateTime.now());
            savedRef.set(incident);
            return incident;
        });
        when(incidentRepository.findDetailById(202L)).thenAnswer(invocation -> Optional.of(savedRef.get()));

        IncidentResponseDTO result = service.createIncident(CreateIncidentRequestDTO.builder()
                .incidentTypeId(activeType.getId())
                .title("Incident")
                .description("Desc")
                .build());

        assertNull(result.getRelatedTeacher());
    }

    @Test
    void create_WithCookingAuditIds_ShouldAttachAudits() {
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        AtomicReference<Incident> savedRef = new AtomicReference<>();
        when(incidentTypeRepository.findById(activeType.getId())).thenReturn(Optional.of(activeType));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setId(203L);
            incident.setCreatedAt(LocalDateTime.now());
            savedRef.set(incident);
            return incident;
        });
        when(recipeCookingAuditRepository.findAllByIdWithUser(List.of(100L, 101L))).thenReturn(List.of(auditByChef, auditByStudent));
        when(incidentAuditAttachmentRepository.findAttachedCookingAuditIds(203L, List.of(100L, 101L))).thenReturn(List.of());
        when(incidentRepository.findDetailById(203L)).thenAnswer(invocation -> Optional.of(savedRef.get()));

        service.createIncident(CreateIncidentRequestDTO.builder()
                .incidentTypeId(activeType.getId())
                .title("Incident")
                .description("Desc")
                .cookingAuditIds(List.of(100L, 101L))
                .build());

        verify(incidentAuditAttachmentRepository).saveAll(org.mockito.ArgumentMatchers.argThat((List<IncidentAuditAttachment> attachments) ->
            attachments.size() == 2
                && attachments.stream().allMatch(attachment -> attachment.getIncident().getId().equals(203L))));
    }

    @Test
    void create_WithInactiveType_ShouldThrow() {
        when(securityContextHelper.getCurrentUser()).thenReturn(chef);
        when(incidentTypeRepository.findById(inactiveType.getId())).thenReturn(Optional.of(inactiveType));

        assertThrows(InvalidOperationException.class, () -> service.createIncident(CreateIncidentRequestDTO.builder()
                .incidentTypeId(inactiveType.getId())
                .title("Incident")
                .description("Desc")
                .build()));
    }

    @Test
    void open_WhenCreado_ShouldTransitionToAbierto() {
        Incident incident = incident(300L, IncidentStatus.CREADO, chef, null, activeType);
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(incidentRepository.findDetailById(300L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentResponseDTO result = service.openIncident(300L, OpenIncidentRequestDTO.builder()
                .severity(IncidentSeverity.ALTA)
                .build());

        assertEquals(IncidentStatus.ABIERTO, result.getStatus());
        assertEquals(IncidentSeverity.ALTA, result.getSeverity());
        assertNotNull(incident.getOpenedAt());
        assertEquals(admin.getId(), incident.getOpenedBy().getId());
    }

    @Test
    void open_WhenAlreadyAbierto_ShouldThrow() {
        Incident incident = incident(301L, IncidentStatus.ABIERTO, chef, null, activeType);
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(incidentRepository.findDetailById(301L)).thenReturn(Optional.of(incident));

        assertThrows(InvalidOperationException.class,
                () -> service.openIncident(301L, OpenIncidentRequestDTO.builder().severity(IncidentSeverity.MEDIA).build()));
    }

    @Test
    void close_WithResolution_ShouldTransitionToCerradoConResolucion() {
        Incident incident = incident(302L, IncidentStatus.ABIERTO, chef, null, activeType);
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(incidentRepository.findDetailById(302L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentResponseDTO result = service.closeIncident(302L,
                CloseIncidentRequestDTO.builder().hasResolution(true).resolution("Fixed").build());

        assertEquals(IncidentStatus.CERRADO_CON_RESOLUCION, result.getStatus());
        assertEquals("Fixed", result.getResolution());
        assertEquals(admin.getId(), incident.getClosedBy().getId());
        assertNotNull(incident.getClosedAt());
    }

    @Test
    void close_WithoutResolution_ShouldTransitionToCerradoSinResolucion() {
        Incident incident = incident(303L, IncidentStatus.ABIERTO, chef, null, activeType);
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(incidentRepository.findDetailById(303L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentResponseDTO result = service.closeIncident(303L,
                CloseIncidentRequestDTO.builder().hasResolution(false).resolution(null).build());

        assertEquals(IncidentStatus.CERRADO_SIN_RESOLUCION, result.getStatus());
        assertNull(result.getResolution());
    }

    @Test
    void close_WhenCreado_ShouldThrow() {
        Incident incident = incident(304L, IncidentStatus.CREADO, chef, null, activeType);
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(incidentRepository.findDetailById(304L)).thenReturn(Optional.of(incident));

        assertThrows(InvalidOperationException.class,
                () -> service.closeIncident(304L, CloseIncidentRequestDTO.builder().hasResolution(false).build()));
    }

    @Test
    void attachAudit_ChefAttachesOwnAudit_ShouldSucceed() {
        Incident incident = incident(400L, IncidentStatus.CREADO, chef, null, activeType);
        when(securityContextHelper.getCurrentUser()).thenReturn(chef);
        when(incidentRepository.findDetailById(400L)).thenReturn(Optional.of(incident));
        when(recipeCookingAuditRepository.findAllByIdWithUser(List.of(100L))).thenReturn(List.of(auditByChef));
        when(incidentAuditAttachmentRepository.findAttachedCookingAuditIds(400L, List.of(100L))).thenReturn(List.of());
        when(incidentParticipantService.allowedAuditUserIds(chef)).thenReturn(java.util.Set.of(chef.getId(), chefStudent.getId()));
        when(incidentAuditAttachmentRepository.findByIncidentId(400L)).thenReturn(List.of());

        List<IncidentAuditAttachmentResponseDTO> result = service.attachAudits(400L,
                AttachAuditRequestDTO.builder().cookingAuditIds(List.of(100L)).build());

        assertNotNull(result);
        verify(incidentAuditAttachmentRepository).saveAll(org.mockito.ArgumentMatchers.argThat((List<IncidentAuditAttachment> attachments) ->
            attachments.size() == 1
                && attachments.get(0).getIncident().getId().equals(400L)));
    }

    @Test
    void attachAudit_ChefAttachesStudentAudit_ShouldSucceed() {
        Incident incident = incident(401L, IncidentStatus.CREADO, chef, null, activeType);
        when(securityContextHelper.getCurrentUser()).thenReturn(chef);
        when(incidentRepository.findDetailById(401L)).thenReturn(Optional.of(incident));
        when(recipeCookingAuditRepository.findAllByIdWithUser(List.of(101L))).thenReturn(List.of(auditByStudent));
        when(incidentAuditAttachmentRepository.findAttachedCookingAuditIds(401L, List.of(101L))).thenReturn(List.of());
        when(incidentParticipantService.allowedAuditUserIds(chef)).thenReturn(java.util.Set.of(chef.getId(), chefStudent.getId()));

        service.attachAudits(401L, AttachAuditRequestDTO.builder().cookingAuditIds(List.of(101L)).build());

        verify(incidentAuditAttachmentRepository).saveAll(org.mockito.ArgumentMatchers.argThat((List<IncidentAuditAttachment> attachments) ->
            attachments.size() == 1
                && attachments.get(0).getIncident().getId().equals(401L)));
    }

    @Test
    void attachAudit_ChefAttachesOtherChefStudentAudit_ShouldThrow() {
        User foreignStudent = user(10, "Foreign Student", "fstd", Role.USER, chefTwo);
        RecipeCookingAudit foreignAudit = RecipeCookingAudit.builder()
                .id(999L)
                .recipe(auditByChef.getRecipe())
                .user(foreignStudent)
                .quantityCooked(new BigDecimal("1.0"))
                .build();
        Incident incident = incident(402L, IncidentStatus.CREADO, chef, null, activeType);

        when(securityContextHelper.getCurrentUser()).thenReturn(chef);
        when(incidentRepository.findDetailById(402L)).thenReturn(Optional.of(incident));
        when(recipeCookingAuditRepository.findAllByIdWithUser(List.of(999L))).thenReturn(List.of(foreignAudit));
        when(incidentAuditAttachmentRepository.findAttachedCookingAuditIds(402L, List.of(999L))).thenReturn(List.of());
        when(incidentParticipantService.allowedAuditUserIds(chef)).thenReturn(java.util.Set.of(chef.getId(), chefStudent.getId()));

        assertThrows(InvalidOperationException.class,
                () -> service.attachAudits(402L, AttachAuditRequestDTO.builder().cookingAuditIds(List.of(999L)).build()));
    }

    @Test
    void attachAudit_WhenIncidentClosed_ShouldThrow() {
        Incident incident = incident(403L, IncidentStatus.CERRADO_CON_RESOLUCION, chef, null, activeType);
        when(securityContextHelper.getCurrentUser()).thenReturn(chef);
        when(incidentRepository.findDetailById(403L)).thenReturn(Optional.of(incident));

        assertThrows(InvalidOperationException.class,
                () -> service.attachAudits(403L, AttachAuditRequestDTO.builder().cookingAuditIds(List.of(100L)).build()));
    }

    @Test
    void revertAudit_AsAdmin_ShouldCallRevertCookingAndMarkAttachment() {
        Incident incident = incident(500L, IncidentStatus.ABIERTO, chef, null, activeType);
        IncidentAuditAttachment attachment = IncidentAuditAttachment.builder()
                .id(700L)
                .incident(incident)
                .cookingAudit(auditByChef)
                .reverted(false)
                .build();

        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(incidentAuditAttachmentRepository.findByIdWithDetails(700L)).thenReturn(Optional.of(attachment));
        when(incidentAuditAttachmentRepository.save(any(IncidentAuditAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentAuditAttachmentResponseDTO result = service.revertAuditFromIncident(
                500L,
                700L,
                RevertAuditFromIncidentRequestDTO.builder().auditAttachmentId(700L).reason("Incorrect cooking").build()
        );

        assertTrue(result.isReverted());
        assertNotNull(result.getRevertedAt());
        verify(recipeService).revertCooking(100L, "Incorrect cooking");

        var order = inOrder(recipeService, incidentAuditAttachmentRepository);
        order.verify(recipeService).revertCooking(100L, "Incorrect cooking");
        order.verify(incidentAuditAttachmentRepository).save(attachment);
    }

    @Test
    void findAll_AsAdmin_ShouldReturnAllOrderedBySeverity() {
        Incident high = incident(1L, IncidentStatus.ABIERTO, chef, null, activeType);
        high.setSeverity(IncidentSeverity.ALTA);
        Incident medium = incident(2L, IncidentStatus.ABIERTO, chef, null, activeType);
        medium.setSeverity(IncidentSeverity.MEDIA);
        Incident low = incident(3L, IncidentStatus.ABIERTO, chef, null, activeType);
        low.setSeverity(IncidentSeverity.BAJA);
        Incident none = incident(4L, IncidentStatus.CREADO, chef, null, activeType);
        none.setSeverity(null);

        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(incidentRepository.findAll(specAny(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(high, medium, low, none), PageRequest.of(0, 20), 4));
        when(incidentChatMessageRepository.countByIncidentIds(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(
                        projection(1L, 2L),
                        projection(2L, 1L),
                        projection(3L, 0L),
                        projection(4L, 0L)
                ));

        Page<IncidentListResponseDTO> result = service.listIncidents(
                null, null, null, null, null, null, PageRequest.of(0, 20));

        assertEquals(4, result.getTotalElements());
        assertEquals(IncidentSeverity.ALTA, result.getContent().get(0).getSeverity());
        assertEquals(IncidentSeverity.MEDIA, result.getContent().get(1).getSeverity());
        assertEquals(IncidentSeverity.BAJA, result.getContent().get(2).getSeverity());
        assertNull(result.getContent().get(3).getSeverity());
    }

    @Test
    void findAll_AsChef_ShouldReturnOwnAndStudentsIncidents() {
        Incident own = incident(10L, IncidentStatus.CREADO, chef, null, activeType);
        Incident student = incident(11L, IncidentStatus.CREADO, chefStudent, chef, activeType);

        when(securityContextHelper.getCurrentUser()).thenReturn(chef);
        when(incidentRepository.findAll(specAny(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(own, student), PageRequest.of(0, 20), 2));
        when(incidentChatMessageRepository.countByIncidentIds(List.of(10L, 11L)))
                .thenReturn(List.of(projection(10L, 0L), projection(11L, 0L)));

        Page<IncidentListResponseDTO> result = service.listIncidents(
                null, null, null, null, null, null, PageRequest.of(0, 20));

        assertEquals(2, result.getContent().size());
        assertEquals(chef.getId(), result.getContent().get(0).getCreatedBy().getId());
        assertEquals(chefStudent.getId(), result.getContent().get(1).getCreatedBy().getId());
    }

    @Test
    void create_WhenCurrentUserIsUnauthorized_ShouldThrowAccessDenied() {
        User regular = user(9, "Regular", "regular", Role.USER, null);
        when(securityContextHelper.getCurrentUser()).thenReturn(regular);

        assertThrows(AccessDeniedException.class,
                () -> service.createIncident(CreateIncidentRequestDTO.builder()
                        .incidentTypeId(activeType.getId())
                        .title("Incident")
                        .description("Desc")
                        .build()));

        verify(incidentRepository, never()).save(any(Incident.class));
    }

    private User user(Integer id, String name, String username, Role role, User teacher) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setUser(username);
        user.setRole(role);
        user.setTeacher(teacher);
        return user;
    }

    private Incident incident(Long id, IncidentStatus status, User createdBy, User relatedTeacher, IncidentType type) {
        Incident incident = Incident.builder()
                .id(id)
                .title("Incident " + id)
                .description("Desc")
                .incidentType(type)
                .status(status)
                .createdBy(createdBy)
                .relatedTeacher(relatedTeacher)
                .createdAt(LocalDateTime.now())
                .build();
        return incident;
    }

    private IncidentChatMessageCountProjection projection(Long incidentId, Long messageCount) {
        return new IncidentChatMessageCountProjection() {
            @Override
            public Long getIncidentId() {
                return incidentId;
            }

            @Override
            public Long getMessageCount() {
                return messageCount;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Specification<Incident> specAny() {
        return (Specification<Incident>) (Specification) any(Specification.class);
    }

}
