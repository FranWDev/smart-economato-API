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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    @Mock private StockLedgerService     stockLedgerService;
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
                stockLedgerService,
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
        recipe.setComponents(List.of(compA, compB));
        return recipe;
    }

    private ProductConsumptionResponseDTO historyFor(Integer productId, int entries) {
        List<DailyConsumptionDTO> breakdown = java.util.stream.IntStream.range(0, entries)
                .mapToObj(i -> new DailyConsumptionDTO(
                        LocalDate.now().minusDays(entries - i),
                        new BigDecimal(String.valueOf(i + 1))))
                .collect(java.util.stream.Collectors.toList());

        return ProductConsumptionResponseDTO.builder()
                .productId(productId)
                .productName("Product " + productId)
                .breakdown(breakdown)
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("productHistories is embedded in the Kafka event with actual breakdown data")
    void productHistories_areEmbeddedInKafkaEvent() throws Throwable {
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

        // Mock history: PRODUCT_A has 30 data points, PRODUCT_B has 15
        given(stockLedgerService.getProductConsumption(eq(PRODUCT_A), any(), any()))
                .willReturn(historyFor(PRODUCT_A, 30));
        given(stockLedgerService.getProductConsumption(eq(PRODUCT_B), any(), any()))
                .willReturn(historyFor(PRODUCT_B, 15));

        // when
        aspect.logCookingAction(joinPoint, auditable);

        // then — capture the event published to Kafka
        ArgumentCaptor<RecipeCookingAuditEvent> captor =
                ArgumentCaptor.forClass(RecipeCookingAuditEvent.class);
        verify(auditEventProducer).publishRecipeCookingAudit(captor.capture());

        RecipeCookingAuditEvent event = captor.getValue();
        assertThat(event.getProductHistories())
                .as("productHistories must be present in the event")
                .isNotNull()
                .isNotEmpty();

        List<DailyConsumption> historyA = event.getProductHistories().get(PRODUCT_A);
        assertThat(historyA)
                .as("Product %d should have 30 history entries", PRODUCT_A)
                .hasSize(30);
        assertThat(historyA.get(0).getDate()).isNotNull();
        assertThat(historyA.get(0).getConsumed()).isPositive();

        List<DailyConsumption> historyB = event.getProductHistories().get(PRODUCT_B);
        assertThat(historyB)
                .as("Product %d should have 15 history entries", PRODUCT_B)
                .hasSize(15);
    }

    @Test
    @DisplayName("productHistories is empty map if stockLedgerService throws for a product")
    void productHistories_gracefullyHandlesServiceError() throws Throwable {
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

        // One product throws, the other succeeds
        given(stockLedgerService.getProductConsumption(eq(PRODUCT_A), any(), any()))
                .willThrow(new RuntimeException("DB unavailable"));
        given(stockLedgerService.getProductConsumption(eq(PRODUCT_B), any(), any()))
                .willReturn(historyFor(PRODUCT_B, 10));

        // when
        aspect.logCookingAction(joinPoint, auditable);

        // then — event must still be published, with partial data
        ArgumentCaptor<RecipeCookingAuditEvent> captor =
                ArgumentCaptor.forClass(RecipeCookingAuditEvent.class);
        verify(auditEventProducer).publishRecipeCookingAudit(captor.capture());

        RecipeCookingAuditEvent event = captor.getValue();
        assertThat(event.getProductHistories()).containsKey(PRODUCT_A);
        assertThat(event.getProductHistories().get(PRODUCT_A))
                .as("Failed product should have empty list (not null)")
                .isEmpty();
        assertThat(event.getProductHistories().get(PRODUCT_B)).hasSize(10);
    }

    @Test
    @DisplayName("productHistories contains empty list if stockLedger has no consumption data")
    void productHistories_emptyWhenNoLedgerData() throws Throwable {
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

        // No consumption data for either product
        given(stockLedgerService.getProductConsumption(any(), any(), any()))
                .willReturn(ProductConsumptionResponseDTO.builder()
                        .breakdown(List.of())
                        .build());

        // when
        aspect.logCookingAction(joinPoint, auditable);

        // then
        ArgumentCaptor<RecipeCookingAuditEvent> captor =
                ArgumentCaptor.forClass(RecipeCookingAuditEvent.class);
        verify(auditEventProducer).publishRecipeCookingAudit(captor.capture());

        RecipeCookingAuditEvent event = captor.getValue();
        // productHistories should still be present but with empty lists
        assertThat(event.getProductHistories()).isNotNull();
        event.getProductHistories().values().forEach(
                list -> assertThat(list).isEmpty()
        );
    }
}
