package com.economato.inventory.infrastructure.aspect.stock;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO;

import com.economato.inventory.application.dto.stock.event.StockPredictionEvent;
import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.dto.stock.request.BatchStockMovementRequestDTO;
import com.economato.inventory.application.dto.stock.request.ManualStockAdjustmentRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.domain.stock.PredictorTrigger;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanSlotRepository;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
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
    @Mock private StockLedgerRepository stockLedgerRepository;
    @Mock private RecipeCookingAuditRepository recipeCookingAuditRepository;
    @Mock private WeeklyPlanSlotRepository weeklyPlanSlotRepository;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private PredictorTrigger trigger;

    private PredictorTriggerAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new PredictorTriggerAspect(
                stockLedgerService,
                auditEventProducer,
                recipeRepository,
                stockLedgerRepository,
                recipeCookingAuditRepository,
                weeklyPlanSlotRepository,
                securityContextHelper
        );
    }

    @Test
    @DisplayName("COOK_RECIPE action triggers event with recipe components history")
    void cookRecipe_triggersEvent() throws Throwable {
        // given
        RecipeCookingRequestDTO request = new RecipeCookingRequestDTO(1, BigDecimal.ONE, "test", null);
        given(trigger.action()).willReturn("COOK_RECIPE");
        given(joinPoint.getArgs()).willReturn(new Object[]{request});
        given(joinPoint.proceed()).willReturn(null);

        Product product = new Product();
        product.setId(101);
        product.setName("Test Product");

        RecipeComponent component = new RecipeComponent();
        component.setProduct(product);

        Recipe recipe = new Recipe();
        recipe.setId(1);
        recipe.setComponents(Set.of(component));

        given(recipeRepository.findProductIdsByRecipeId(1)).willReturn(List.of(101));
        
        Map<Integer, List<DailyConsumptionDTO>> history = new HashMap<>();
        history.put(101, List.of(new DailyConsumptionDTO(LocalDate.now(), BigDecimal.TEN)));
        given(stockLedgerService.getDailyConsumptionBatch(anyList(), any(), any())).willReturn(history);

        // when
        aspect.aroundModification(joinPoint, trigger);

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
    void manualAdjustment_triggersEvent() throws Throwable {
        // given
        ManualStockAdjustmentRequestDTO request = new ManualStockAdjustmentRequestDTO(
                202, BigDecimal.ONE, MovementType.ENTRADA, "test", null, null
        );
        given(trigger.action()).willReturn("MANUAL_ADJUSTMENT");
        given(joinPoint.getArgs()).willReturn(new Object[]{request});
        given(joinPoint.proceed()).willReturn(null);

        given(stockLedgerService.getDailyConsumptionBatch(anyList(), any(), any())).willReturn(Collections.emptyMap());

        // when
        aspect.aroundModification(joinPoint, trigger);

        // then
        ArgumentCaptor<StockPredictionEvent> captor = ArgumentCaptor.forClass(StockPredictionEvent.class);
        verify(auditEventProducer).publishStockPredictionEvent(captor.capture());

        StockPredictionEvent event = captor.getValue();
        assertThat(event.getTriggerType()).isEqualTo("MANUAL_ADJUSTMENT");
        assertThat(event.getAffectedProductIds()).containsExactly(202);
    }

    @Test
    @DisplayName("BATCH_MOVEMENT action triggers event from result list")
    void batchMovement_triggersEvent() throws Throwable {
        // given
        BatchMovementItem item1 = new BatchMovementItem(303, BigDecimal.ONE, MovementType.SALIDA, "test", null);
        List<BatchMovementItem> requests = List.of(item1);

        given(trigger.action()).willReturn("BATCH_MOVEMENT");
        given(joinPoint.getArgs()).willReturn(new Object[]{requests});
        given(joinPoint.proceed()).willReturn(null);
        
        given(stockLedgerService.getDailyConsumptionBatch(anyList(), any(), any())).willReturn(Collections.emptyMap());

        // when
        aspect.aroundModification(joinPoint, trigger);

        // then
        ArgumentCaptor<StockPredictionEvent> captor = ArgumentCaptor.forClass(StockPredictionEvent.class);
        verify(auditEventProducer).publishStockPredictionEvent(captor.capture());

        StockPredictionEvent event = captor.getValue();
        assertThat(event.getTriggerType()).isEqualTo("BATCH_MOVEMENT");
        assertThat(event.getAffectedProductIds()).containsExactly(303);
    }
}
