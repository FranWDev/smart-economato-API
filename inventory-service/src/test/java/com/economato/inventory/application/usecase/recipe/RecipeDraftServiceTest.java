package com.economato.inventory.application.usecase.recipe;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;

import com.economato.inventory.application.dto.recipe.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeDraftRejectRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeDraftRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeRequestDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeDraftResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeResponseDTO;
import com.economato.inventory.application.mapper.recipe.RecipeDraftMapper;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.RecipeDraft;
import com.economato.inventory.domain.model.recipe.RecipeDraftStatus;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeDraftRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeDraftServiceTest {

    @Mock
    private RecipeDraftRepository recipeDraftRepository;
    @Mock
    private RecipeService recipeService;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private I18nService i18nService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RecipeDraftMapper recipeDraftMapper;
    @Mock
    private PersistentNotificationService persistentNotificationService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RecipeDraftService recipeDraftService;

    private User regularUser;
    private User adminUser;
    private Product product;
    private RecipeDraftRequestDTO validRequest;

    @BeforeEach
    void setUp() {
        regularUser = new User();
        regularUser.setId(10);
        regularUser.setName("User Draft");
        regularUser.setRole(Role.USER);

        adminUser = new User();
        adminUser.setId(1);
        adminUser.setName("Admin Draft");
        adminUser.setRole(Role.ADMIN);

        product = new Product();
        product.setId(100);
        product.setName("Harina");

        RecipeComponentRequestDTO component = new RecipeComponentRequestDTO();
        component.setProductId(product.getId());
        component.setQuantity(new BigDecimal("1.5"));

        validRequest = new RecipeDraftRequestDTO();
        validRequest.setName("Paella draft");
        validRequest.setElaboration("Paso 1");
        validRequest.setPresentation("Plato hondo");
        validRequest.setPortions(new BigDecimal("2"));
        validRequest.setComponents(List.of(component));
        validRequest.setAllergenIds(List.of());
        validRequest.setHidden(false);

        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).name());
        lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).name());
        lenient().when(i18nService.getMessage(any(MessageKey.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).name());

        lenient().when(recipeDraftMapper.readComponents(anyString(), any()))
                .thenReturn(validRequest.getComponents());
        lenient().when(recipeDraftMapper.readAllergenIds(anyString(), any()))
                .thenReturn(List.of());
        lenient().when(recipeDraftMapper.toResponseDTO(any(RecipeDraft.class)))
                .thenAnswer(invocation -> {
                    RecipeDraft draft = invocation.getArgument(0);
                    RecipeDraftResponseDTO dto = new RecipeDraftResponseDTO();
                    dto.setId(draft.getId());
                    dto.setName(draft.getName());
                    dto.setStatus(draft.getStatus());
                    dto.setApprovedRecipeId(draft.getApprovedRecipeId());
                    return dto;
                });
    }

    @Test
    void createDraft_WhenValidRequest_ShouldPersistAndReturnPending() {
        when(securityContextHelper.getCurrentUser()).thenReturn(regularUser);
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(List.of(adminUser));
        when(recipeDraftRepository.save(any(RecipeDraft.class))).thenAnswer(invocation -> {
            RecipeDraft draft = invocation.getArgument(0);
            draft.setId(200);
            return draft;
        });

        RecipeDraftResponseDTO response = recipeDraftService.createDraft(validRequest);

        assertNotNull(response);
        assertEquals(200, response.getId());
        assertEquals(RecipeDraftStatus.PENDING, response.getStatus());
        verify(recipeDraftRepository).save(any(RecipeDraft.class));
        verify(persistentNotificationService).notifyUsersOfType(any(), eq("Paella draft"), anyString(), eq(200L), anyList());
    }

    @Test
    void createDraft_WhenRequestContainsMissingProduct_ShouldThrowNotFound() {
        when(securityContextHelper.getCurrentUser()).thenReturn(regularUser);
        when(productRepository.findAllById(any())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> recipeDraftService.createDraft(validRequest));

        verify(recipeDraftRepository, never()).save(any(RecipeDraft.class));
    }

    @Test
    void approveDraft_WhenPending_ShouldSetApprovedAndCreateRecipe() {
        RecipeDraft draft = RecipeDraft.builder()
                .id(300)
                .name("Draft to approve")
                .elaboration("Paso")
                .presentation("Presentacion")
                .portions(BigDecimal.ONE)
                .componentsJson("[]")
                .allergenIdsJson("[]")
                .status(RecipeDraftStatus.PENDING)
                .createdBy(regularUser)
                .build();

        RecipeResponseDTO createdRecipe = new RecipeResponseDTO();
        createdRecipe.setId(999);

        when(securityContextHelper.getCurrentUser()).thenReturn(adminUser);
        when(recipeDraftRepository.findById(300)).thenReturn(Optional.of(draft));
        when(recipeDraftMapper.toRecipeRequestDTO(eq(draft), any())).thenReturn(new RecipeRequestDTO());
        when(recipeService.save(any(RecipeRequestDTO.class))).thenReturn(createdRecipe);
        when(recipeDraftRepository.save(any(RecipeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecipeDraftResponseDTO response = recipeDraftService.approveDraft(300);

        assertEquals(RecipeDraftStatus.APPROVED, response.getStatus());
        assertEquals(999, response.getApprovedRecipeId());
        verify(recipeService).save(any(RecipeRequestDTO.class));
        verify(persistentNotificationService).createNotification(eq(regularUser), eq(adminUser), any(), eq("Draft to approve"), anyString(), eq(300L), eq("recipe-draft-300"));
    }

    @Test
    void updateDraft_WhenRejected_ShouldResubmitAsPending() {
        RecipeDraft rejectedDraft = RecipeDraft.builder()
                .id(333)
                .name("Rejected")
                .elaboration("old")
                .presentation("old")
                .portions(BigDecimal.ONE)
                .componentsJson("[]")
                .allergenIdsJson("[]")
                .status(RecipeDraftStatus.REJECTED)
                .createdBy(regularUser)
                .rejectionReason("missing details")
                .approvedRecipeId(88)
                .reviewedBy(adminUser)
                .build();

        when(securityContextHelper.getCurrentUser()).thenReturn(regularUser);
        when(recipeDraftRepository.findById(333)).thenReturn(Optional.of(rejectedDraft));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(List.of(adminUser));
        when(recipeDraftRepository.save(any(RecipeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecipeDraftResponseDTO response = recipeDraftService.updateDraft(333, validRequest);

        assertEquals(RecipeDraftStatus.PENDING, response.getStatus());
        verify(persistentNotificationService).notifyUsersOfType(any(), eq("Paella draft"), anyString(), eq(333L), anyList());
    }

    @Test
    void deleteDraft_WhenNotOwner_ShouldThrowInvalidOperation() {
        User otherUser = new User();
        otherUser.setId(99);
        otherUser.setRole(Role.USER);

        RecipeDraft draft = RecipeDraft.builder()
                .id(404)
                .name("Other")
                .status(RecipeDraftStatus.PENDING)
                .createdBy(otherUser)
                .componentsJson("[]")
                .build();

        when(securityContextHelper.getCurrentUser()).thenReturn(regularUser);
        when(recipeDraftRepository.findById(404)).thenReturn(Optional.of(draft));

        assertThrows(InvalidOperationException.class, () -> recipeDraftService.deleteDraft(404));
        verify(recipeDraftRepository, never()).delete(any(RecipeDraft.class));
    }

    @Test
    void rejectDraft_WhenNotPending_ShouldThrowInvalidOperation() {
        RecipeDraft nonPending = RecipeDraft.builder()
                .id(500)
                .name("Already approved")
                .status(RecipeDraftStatus.APPROVED)
                .createdBy(regularUser)
                .componentsJson("[]")
                .build();

        RecipeDraftRejectRequestDTO rejectRequest = new RecipeDraftRejectRequestDTO();
        rejectRequest.setReason("Needs update");

        when(securityContextHelper.getCurrentUser()).thenReturn(adminUser);
        when(recipeDraftRepository.findById(500)).thenReturn(Optional.of(nonPending));

        assertThrows(InvalidOperationException.class, () -> recipeDraftService.rejectDraft(500, rejectRequest));
        verify(recipeDraftRepository, never()).save(any(RecipeDraft.class));
    }
}
