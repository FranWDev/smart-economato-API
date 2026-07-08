package com.economato.inventory.application.usecase.recipe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.recipe.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.mapper.recipe.RecipeMapper;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.weeklyplan.StudentSlotStatus;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlan;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlot;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlotStatus;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanStatus;
import com.economato.inventory.domain.recipe.RecipeCookingAuditable;
import com.economato.inventory.domain.stock.PredictorTrigger;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanSlotRepository;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class })
public class RecipeCookingProcessor {

    private final I18nService i18nService;
    private final RecipeRepository repository;
    private final ProductRepository productRepository;
    private final RecipeMapper recipeMapper;
    private final StockLedgerService stockLedgerService;
    private final SecurityContextHelper securityContextHelper;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final WeeklyPlanSlotRepository weeklyPlanSlotRepository;
    private final WeeklyPlanRepository weeklyPlanRepository;

    public RecipeCookingProcessor(I18nService i18nService,
                                  RecipeRepository repository,
                                  ProductRepository productRepository,
                                  RecipeMapper recipeMapper,
                                  StockLedgerService stockLedgerService,
                                  SecurityContextHelper securityContextHelper,
                                  RecipeCookingAuditRepository recipeCookingAuditRepository,
                                  WeeklyPlanSlotRepository weeklyPlanSlotRepository,
                                  WeeklyPlanRepository weeklyPlanRepository) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.productRepository = productRepository;
        this.recipeMapper = recipeMapper;
        this.stockLedgerService = stockLedgerService;
        this.securityContextHelper = securityContextHelper;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
        this.weeklyPlanSlotRepository = weeklyPlanSlotRepository;
        this.weeklyPlanRepository = weeklyPlanRepository;
    }

    @RealtimeSync(entityType = "recipe", action = "CONFIRM", idFromArg = -2,
            affectedDomains = {"recipe", "ledger", "product", "weekly_plan", "stock_alerts"},
            idsFromResult = "recipeProductIds")
    @PredictorTrigger(action = "COOK_RECIPE")
    @RecipeCookingAuditable(action = "COOK_RECIPE")
    public RecipeResponseDTO cookRecipe(RecipeCookingRequestDTO cookingRequest) {
        String correlationId = UUID.randomUUID().toString();
        cookingRequest.setCorrelationId(correlationId);

        log.info("Iniciando proceso de cocinado de receta: recipeId={}, cantidad={}, correlationId={}",
                cookingRequest.getRecipeId(), cookingRequest.getQuantity(), correlationId);

        Recipe recipe = repository.findByIdWithDetails(cookingRequest.getRecipeId())
                .orElseThrow(
                        () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND,
                                new Object[] { cookingRequest.getRecipeId() })));

        if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NO_COMPONENTS));
        }

        User currentUser = securityContextHelper.getCurrentUser();

        List<Integer> componentProductIds = recipe.getComponents().stream()
                .map(c -> c.getProduct().getId())
                .toList();
        Map<Integer, Product> productsById = productRepository.findAllById(componentProductIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        if (productsById.size() != componentProductIds.size()) {
            throw new ResourceNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND, new Object[] { "multiple" }));
        }

        List<BatchMovementItem> movements = new ArrayList<>();
        for (RecipeComponent component : recipe.getComponents()) {
            Product product = productsById.get(component.getProduct().getId());

            BigDecimal requiredQuantity = component.getQuantity().multiply(cookingRequest.getQuantity());

            BigDecimal availabilityPercent = product.getAvailabilityPercentage() != null
                    ? product.getAvailabilityPercentage()
                    : BigDecimal.valueOf(100.00);

            BigDecimal usableStock = product.getCurrentStock()
                    .multiply(availabilityPercent)
                    .divide(BigDecimal.valueOf(100), 3, RoundingMode.DOWN);

            if (usableStock.compareTo(requiredQuantity) < 0) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_RECIPE_STOCK_INSUFFICIENT,
                                new Object[] { product.getName(), requiredQuantity, usableStock }));
            }

            BigDecimal grossQuantity = availabilityPercent.compareTo(BigDecimal.ZERO) > 0
                    ? requiredQuantity.multiply(BigDecimal.valueOf(100)).divide(availabilityPercent, 3, RoundingMode.HALF_UP)
                    : requiredQuantity;

            movements.add(new BatchMovementItem(
                    product.getId(),
                    grossQuantity.negate(),
                    MovementType.SALIDA,
                    i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_COOKING,
                            new Object[] { recipe.getName(), cookingRequest.getQuantity() }),
                    null,
                    correlationId));

            log.info("Stock Bruto descontado del ledger: producto={}, neto={}, bruto={}",
                    product.getName(), requiredQuantity, grossQuantity);
        }

        stockLedgerService.recordBatchStockMovements(movements, currentUser, null);

        log.info("Receta cocinada exitosamente: receta={}, cantidad={}, usuario={}",
                recipe.getName(), cookingRequest.getQuantity(),
                currentUser != null ? currentUser.getName() : "Sistema");

        return repository.findProjectedById(recipe.getId())
                .map(recipeMapper::toResponseDTO)
                .orElseGet(() -> recipeMapper.toResponseDTO(recipe));
    }

    @RealtimeSync(entityType = "recipe", action = "REVERT", idFromArg = -2,
            affectedDomains = {"recipe", "ledger", "product", "weekly_plan", "stock_alerts"},
            idsFromResult = "recipeProductIds")
    @PredictorTrigger(action = "REVERT_COOKING")
    public List<Integer> revertCooking(Long auditId, String reason) {
        log.info("Iniciando reversión de cocinado: auditId={}, motivo={}", auditId, reason);

        try {
            var audit = recipeCookingAuditRepository.findById(auditId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

            if (audit.getCorrelationId() == null) {
                log.warn("Intento de revertir auditoría sin correlationId: auditId={}", auditId);
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_INTERNAL_SERVER_ERROR));
            }

            String correlationId = audit.getCorrelationId();

            stockLedgerService.revertMovement(correlationId, "Deshacer cocinado: " + reason);
            syncWeeklyPlanSlotAfterExternalRevert(correlationId);

            List<Integer> affectedProductIds = recipeCookingAuditRepository.findProductIdsByAuditId(auditId);

            recipeCookingAuditRepository.delete(audit);

            log.info("Cocinado revertido y auditoría eliminada exitosamente: auditId={}, correlationId={}", auditId, audit.getCorrelationId());
            return affectedProductIds;
        } catch (ResourceNotFoundException | InvalidOperationException e) {
            log.warn("Error validado al revertir cocinado: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al revertir cocinado: {}", e.getMessage(), e);
            throw e;
        }
    }

    public void syncWeeklyPlanSlotAfterExternalRevert(String correlationId) {
        weeklyPlanSlotRepository.findByCorrelationId(correlationId).ifPresent(slot -> {
            WeeklyPlan plan = slot.getWeeklyPlan();
            if (!isRuntimePlanStatus(plan.getStatus()) || slot.getStatus() != WeeklyPlanSlotStatus.CONFIRMED) {
                return;
            }

            slot.setStatus(WeeklyPlanSlotStatus.PENDING);
            slot.setConfirmedAt(null);
            slot.setConfirmedBy(null);
            slot.setCorrelationId(null);

            slot.getStudents().forEach(studentSlot -> {
                if (studentSlot.getStatus() == StudentSlotStatus.CONFIRMED) {
                    studentSlot.setStatus(StudentSlotStatus.ASSIGNED);
                }
            });

            boolean hasConfirmed = plan.getSlots().stream().anyMatch(s -> s.getStatus() == WeeklyPlanSlotStatus.CONFIRMED);
            if (hasConfirmed) {
                plan.setStatus(WeeklyPlanStatus.IN_PROGRESS);
            } else if (plan.getStatus() == WeeklyPlanStatus.IN_PROGRESS) {
                plan.setStatus(WeeklyPlanStatus.ACTIVE);
            }

            weeklyPlanRepository.saveAndFlush(plan);
            weeklyPlanSlotRepository.saveAndFlush(slot);
            log.info("Slot de plan semanal sincronizado tras reversión externa: slotId={}, planId={}", slot.getId(), plan.getId());
        });
    }

    private boolean isRuntimePlanStatus(WeeklyPlanStatus status) {
        return status == WeeklyPlanStatus.ACTIVE || status == WeeklyPlanStatus.IN_PROGRESS;
    }
}
