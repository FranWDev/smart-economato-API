package com.economato.inventory.infrastructure.aspect;

import com.economato.inventory.application.dto.event.StockPredictionEvent;
import com.economato.inventory.application.dto.request.ManualStockAdjustmentRequestDTO;
import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeComponent;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.WeeklyPlanSlotRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PredictorTriggerAspectTest {

    @Mock private StockLedgerService stockLedgerService;
    @Mock private AuditEventProducer auditEventProducer;
    @Mock private RecipeRepository recipeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StockLedgerRepository stockLedgerRepository;
    @Mock private RecipeCookingAuditRepository recipeCookingAuditRepository;
    @Mock private WeeklyPlanSlotRepository weeklyPlanSlotRepository;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private JoinPoint joinPoint;
    @Mock private PredictorTrigger trigger;

    private PredictorTriggerAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new PredictorTriggerAspect(
                stockLedgerService,
                auditEventProducer,
                recipeRepository,
                orderRepository,
                stockLedgerRepository,
                recipeCookingAuditRepository,
                weeklyPlanSlotRepository,
                securityContextHelper
        );
    }

    @Test
    @DisplayName("COOK_RECIPE action triggers event with recipe components history")
    void cookRecipe_triggersEvent() {
        // given
        RecipeCookingRequestDTO request = new RecipeCookingRequestDTO(1, BigDecimal.ONE, "test", null);
        given(trigger.action()).willReturn("COOK_RECIPE");
        given(joinPoint.getArgs()).willReturn(new Object[]{request});

        Product product = new Product();
        product.setId(101);
        product.setName("Test Product");

        RecipeComponent component = new RecipeComponent();
        component.setProduct(product);

        Recipe recipe = new Recipe();
        recipe.setId(1);
        recipe.setComponents(Set.of(component));

        given(recipeRepository.findByIdWithDetails(1)).willReturn(Optional.of(recipe));
        
        Map<Integer, List<DailyConsumptionDTO>> history = new HashMap<>();
        history.put(101, List.of(new DailyConsumptionDTO(LocalDate.now(), BigDecimal.TEN)));
        given(stockLedgerService.getDailyConsumptionBatch(anyList(), any(), any())).willReturn(history);

        // when
        aspect.afterModification(joinPoint, trigger, null);

        // then
        ArgumentCaptor<StockPredictionEvent> captor = ArgumentCaptor.forClass(StockPredictionEvent.class);
        verify(auditEventProducer).publishStockPredictionEvent(captor.capture());

        StockPredictionEvent event = captor.getValue();
        assertThat(event.getTriggerType()).isEqualTo("COOK_RECIPE");
        assertThat(event.getAffectedProductIds()).containsExactly(101);
        assertThat(event.getProductHistories().get(101)).hasSize(1);
    }

    @Test
    @DisplayName("MANUAL_ADJUSTMENT action triggers event for single product")
    void manualAdjustment_triggersEvent() {
        // given
        ManualStockAdjustmentRequestDTO request = new ManualStockAdjustmentRequestDTO(
                202, BigDecimal.ONE, MovementType.ENTRADA, "test", null, null
        );
        given(trigger.action()).willReturn("MANUAL_ADJUSTMENT");
        given(joinPoint.getArgs()).willReturn(new Object[]{request});

        given(stockLedgerService.getDailyConsumptionBatch(anyList(), any(), any())).willReturn(Collections.emptyMap());

        // when
        aspect.afterModification(joinPoint, trigger, null);

        // then
        ArgumentCaptor<StockPredictionEvent> captor = ArgumentCaptor.forClass(StockPredictionEvent.class);
        verify(auditEventProducer).publishStockPredictionEvent(captor.capture());

        StockPredictionEvent event = captor.getValue();
        assertThat(event.getTriggerType()).isEqualTo("MANUAL_ADJUSTMENT");
        assertThat(event.getAffectedProductIds()).containsExactly(202);
    }

    @Test
    @DisplayName("SCHEDULED_REFRESH action triggers event from result list")
    void scheduledRefresh_triggersEvent() {
        // given
        List<Integer> productIds = Arrays.asList(303, 404);
        given(trigger.action()).willReturn("SCHEDULED_REFRESH");
        given(joinPoint.getArgs()).willReturn(new Object[]{});
        
        given(stockLedgerService.getDailyConsumptionBatch(anyList(), any(), any())).willReturn(Collections.emptyMap());

        // when
        aspect.afterModification(joinPoint, trigger, productIds);

        // then
        ArgumentCaptor<StockPredictionEvent> captor = ArgumentCaptor.forClass(StockPredictionEvent.class);
        verify(auditEventProducer).publishStockPredictionEvent(captor.capture());

        StockPredictionEvent event = captor.getValue();
        assertThat(event.getTriggerType()).isEqualTo("SCHEDULED_REFRESH");
        assertThat(event.getAffectedProductIds()).containsExactlyInAnyOrder(303, 404);
    }
}
