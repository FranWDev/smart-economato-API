package com.economato.inventory.application.usecase.weeklyplan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanStockRequirementDTO;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlan;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlot;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlotStatus;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanStatus;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanStockReservationServiceTest {

    @Mock
    private WeeklyPlanRepository weeklyPlanRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductBatchRepository batchRepository;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private WeeklyPlanStockReservationService service;

    @Test
    void isStockSufficientForAction_shouldUseTargetDateWhenProvided() {
        Product product = createProduct(1, "Harina", "KG", new BigDecimal("130.000"));
        LocalDate targetDate = LocalDate.now().plusDays(7);

        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(weeklyPlanRepository.calculateReservedStock(null)).thenReturn(Collections.<Object[]>emptyList());
        when(batchRepository.sumNonExpiredRemainingQuantity(1, targetDate)).thenReturn(new BigDecimal("100.000"));

        assertFalse(service.isStockSufficientForAction(1, new BigDecimal("120.000"), targetDate));
        assertTrue(service.isStockSufficientForAction(1, new BigDecimal("90.000"), targetDate));
    }

    @Test
    void validateStockForPlanActivation_shouldFailWhenEffectiveStockExpiresBeforePlan() {
        LocalDate weekStart = LocalDate.now().plusDays(7);
        WeeklyPlan plan = createPlan(10L, weekStart);
        Product product = createProduct(1, "Leche", "L", new BigDecimal("130.000"));

        when(weeklyPlanRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(weeklyPlanRepository.calculateRequiredStockForPlan(10L))
            .thenReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("120.000")}));
        when(weeklyPlanRepository.calculateReservedStock(10L)).thenReturn(Collections.<Object[]>emptyList());
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product));
        when(batchRepository.sumNonExpiredRemainingQuantity(1, weekStart)).thenReturn(new BigDecimal("100.000"));
        when(i18nService.getMessage(eq(MessageKey.ERROR_WEEKLY_PLAN_STOCK_EXPIRES_BEFORE_PLAN), any(Object[].class)))
                .thenReturn("Not enough effective stock before plan");

        assertThrows(InvalidOperationException.class, () -> service.validateStockForPlanActivation(10L));
    }

    @Test
    void validateStockForPlanUpdate_shouldConsiderOtherPlanReservationsAndPlanWeek() {
        LocalDate weekStart = LocalDate.now().plusDays(7);
        WeeklyPlan existingPlan = createPlan(20L, weekStart);
        Product product = createProduct(2, "Yogur", "UND", new BigDecimal("130.000"));
        existingPlan.getSlots().add(createPendingSlot(existingPlan, product, new BigDecimal("120.000")));

        when(weeklyPlanRepository.calculateReservedStock(20L))
            .thenReturn(List.<Object[]>of(new Object[]{2, new BigDecimal("20.000")}));
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product));
        when(batchRepository.sumNonExpiredRemainingQuantity(2, weekStart)).thenReturn(new BigDecimal("100.000"));
        when(i18nService.getMessage(eq(MessageKey.ERROR_WEEKLY_PLAN_STOCK_EXPIRES_BEFORE_PLAN), any(Object[].class)))
                .thenReturn("Not enough effective stock before plan");

        assertThrows(InvalidOperationException.class,
            () -> service.validateStockForPlanUpdate(existingPlan, List.of(existingPlan.getSlots().iterator().next())));
    }

    @Test
    void getStockRequirements_shouldExposeExpirationMetadata() {
        LocalDate weekStart = LocalDate.now().plusDays(7);
        WeeklyPlan plan = createPlan(30L, weekStart);
        Product product = createProduct(3, "Queso", "KG", new BigDecimal("130.000"));
        product.setLotQuantity(new BigDecimal("1.000"));

        ProductBatch expiringSoon = ProductBatch.builder()
                .id(301L)
                .product(product)
                .expirationDate(LocalDate.now().plusDays(1))
                .remainingQuantity(new BigDecimal("30.000"))
                .depleted(false)
                .receivedAt(LocalDateTime.now().minusDays(2))
                .build();
        ProductBatch validLater = ProductBatch.builder()
                .id(302L)
                .product(product)
                .expirationDate(LocalDate.now().plusDays(30))
                .remainingQuantity(new BigDecimal("100.000"))
                .depleted(false)
                .receivedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(weeklyPlanRepository.findById(30L)).thenReturn(Optional.of(plan));
        when(weeklyPlanRepository.calculateRequiredStockForPlan(30L))
            .thenReturn(List.<Object[]>of(new Object[]{3, new BigDecimal("120.000")}));
        when(weeklyPlanRepository.calculateReservedStock(30L)).thenReturn(Collections.<Object[]>emptyList());
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product));
        when(batchRepository.sumNonExpiredRemainingQuantity(3, weekStart)).thenReturn(new BigDecimal("100.000"));
        when(batchRepository.sumExpiringBeforeDate(3, weekStart)).thenReturn(new BigDecimal("30.000"));
        when(batchRepository.sumExpiredRemainingQuantityByProductId(3)).thenReturn(BigDecimal.ZERO);
        when(batchRepository.findActiveByProductIdOrderByExpiration(3)).thenReturn(List.of(expiringSoon, validLater));

        List<WeeklyPlanStockRequirementDTO> result = service.getStockRequirements(30L);

        assertEquals(1, result.size());
        WeeklyPlanStockRequirementDTO dto = result.get(0);
        assertEquals(3, dto.getProductId());
        assertEquals(0, dto.getRequiredQuantity().compareTo(new BigDecimal("120.000")));
        assertEquals(new BigDecimal("100.000"), dto.getAvailableStock());
        assertEquals(new BigDecimal("30.000"), dto.getExpiringBeforePlanStock());
        assertEquals(LocalDate.now().plusDays(1), dto.getNearestExpirationDate());
        assertTrue(dto.isExpirationRisk());
        assertFalse(dto.isSufficient());
    }

    @Test
    void calculateStockRequirements_shouldPreserveCurrentStockWhenNoPlanDateProvided() {
        Product product = createProduct(4, "Mantequilla", "KG", new BigDecimal("55.000"));
        product.setLotQuantity(new BigDecimal("1.000"));
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product));
        when(weeklyPlanRepository.calculateReservedStock(null)).thenReturn(Collections.<Object[]>emptyList());
        when(batchRepository.sumExpiredRemainingQuantityByProductId(4)).thenReturn(BigDecimal.ZERO);

        List<WeeklyPlanStockRequirementDTO> result = service.calculateStockRequirements(Map.of(4, new BigDecimal("10.000")), null);

        assertEquals(1, result.size());
        WeeklyPlanStockRequirementDTO dto = result.get(0);
        assertEquals(new BigDecimal("55.000"), dto.getAvailableStock());
        assertEquals(new BigDecimal("55.000"), dto.getGrossAvailableStock());
        assertFalse(dto.isExpirationRisk());
    }

    private Product createProduct(Integer id, String name, String unit, BigDecimal currentStock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setUnit(unit);
        product.setCurrentStock(currentStock);
        return product;
    }

    private WeeklyPlan createPlan(Long id, LocalDate weekStart) {
        WeeklyPlan plan = new WeeklyPlan();
        plan.setId(id);
        plan.setWeekStartDate(weekStart);
        plan.setStatus(WeeklyPlanStatus.DRAFT);
        plan.setSlots(new HashSet<>());
        return plan;
    }

    private WeeklyPlanSlot createPendingSlot(WeeklyPlan plan, Product product, BigDecimal quantity) {
        Recipe recipe = new Recipe();
        recipe.setId(1000);
        recipe.setPortions(BigDecimal.ONE);
        RecipeComponent component = new RecipeComponent();
        component.setProduct(product);
        component.setQuantity(BigDecimal.ONE);
        component.setParentRecipe(recipe);
        recipe.setComponents(new HashSet<>(List.of(component)));

        WeeklyPlanSlot slot = new WeeklyPlanSlot();
        slot.setWeeklyPlan(plan);
        slot.setRecipe(recipe);
        slot.setQuantity(quantity);
        slot.setDayOfWeek(1);
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(11, 0));
        slot.setSortOrder(1);
        slot.setStatus(WeeklyPlanSlotStatus.PENDING);
        return slot;
    }
}
