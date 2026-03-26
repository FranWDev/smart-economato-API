package com.economato.inventory.infrastructure.aspect;

import com.economato.inventory.application.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.application.dto.event.RecipeCookingAuditEvent.DailyConsumption;
import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.domain.RecipeCookingAuditable;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeComponent;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RecipeCookingAuditAspect}.
 *
 * Verifies that when a recipe is cooked, the Kafka event contains
 * the embedded product consumption history — so the predictor does
 * not need to make any HTTP calls back to the backend.
 *
 * No Python service, no Kafka broker, no database required.
 */
@ExtendWith(MockitoExtension.class)
class RecipeCookingAuditAspectTest {

    @Mock private RecipeRepository       recipeRepository;
    @Mock private SecurityContextHelper  securityContextHelper;
    @Mock private AuditEventProducer     auditEventProducer;
    @Mock private ObjectMapper           objectMapper;
    @Mock private ProceedingJoinPoint    joinPoint;
    @Mock private RecipeCookingAuditable auditable;

    private RecipeCookingAuditAspect aspect;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private static final Integer PRODUCT_A = 950;
    private static final Integer PRODUCT_B = 402;

    @BeforeEach
    void setUp() {
        aspect = new RecipeCookingAuditAspect(
                recipeRepository,
                securityContextHelper,
                auditEventProducer,
                objectMapper);
    }

    private Recipe buildRecipe() {
        Product prodA = new Product();
        prodA.setId(PRODUCT_A);
        prodA.setName("Queso Roquefort");

        Product prodB = new Product();
        prodB.setId(PRODUCT_B);
        prodB.setName("Espárragos Finos");

        RecipeComponent compA = new RecipeComponent();
        compA.setProduct(prodA);
        compA.setQuantity(new BigDecimal("0.200"));

        RecipeComponent compB = new RecipeComponent();
        compB.setProduct(prodB);
        compB.setQuantity(BigDecimal.ONE);

        Recipe recipe = new Recipe();
        recipe.setId(12);
        recipe.setName("Escalope Milanesa");
        recipe.setComponents(Set.of(compA, compB));
        return recipe;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RecipeCookingAuditEvent is published with null productHistories (migrated to PredictorTriggerAspect)")
    void cooking_publishesEventWithoutEmbeddedHistory() throws Throwable {
        // given
        Recipe recipe = buildRecipe();
        RecipeCookingRequestDTO request = new RecipeCookingRequestDTO();
        request.setRecipeId(12);
        request.setQuantity(BigDecimal.ONE);

        given(joinPoint.getArgs()).willReturn(new Object[]{request});
        given(joinPoint.proceed()).willReturn(null);
        given(recipeRepository.findByIdWithDetails(12)).willReturn(Optional.of(recipe));
        given(securityContextHelper.getCurrentUser()).willReturn(null);
        given(objectMapper.writeValueAsString(any())).willReturn("{\"components\":[]}");

        // when
        aspect.logCookingAction(joinPoint, auditable);

        // then — capture the event published to Kafka
        ArgumentCaptor<RecipeCookingAuditEvent> captor =
                ArgumentCaptor.forClass(RecipeCookingAuditEvent.class);
        verify(auditEventProducer).publishRecipeCookingAudit(captor.capture());

        RecipeCookingAuditEvent event = captor.getValue();
        assertThat(event.getRecipeId()).isEqualTo(12);
        assertThat(event.getProductHistories())
                .as("productHistories must be null in RecipeCookingAuditAspect (now handled by PredictorTriggerAspect)")
                .isNull();
    }
}
