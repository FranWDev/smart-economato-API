package com.economato.inventory.application.usecase.weeklyplan;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.domain.model.order.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.user.projection.UserProjection;
import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.dto.weeklyplan.request.WeeklyPlanRequestDTO;
import com.economato.inventory.application.dto.weeklyplan.request.WeeklyPlanSlotRequestDTO;
import com.economato.inventory.application.dto.shared.response.ConfirmDayResponseDTO;
import com.economato.inventory.application.dto.weeklyplan.response.StudentMetricsResponseDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanSlotStudentResponseDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanStockRequirementDTO;
import com.economato.inventory.application.dto.shared.response.PdfReportResponseDTO;
import com.economato.inventory.application.mapper.weeklyplan.WeeklyPlanMapper;
import com.economato.inventory.domain.stock.PredictorTrigger;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.notification.NotificationType;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;
import com.economato.inventory.domain.model.recipe.RecipeCookingAudit;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.weeklyplan.StudentSlotStatus;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlan;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlot;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlotStatus;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlotStudent;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanStatus;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.notification.NotificationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanSlotRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanSlotStudentRepository;
import com.economato.inventory.infrastructure.adapter.out.external.weeklyplan.reports.WeeklyPlanPdfService;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.weeklyplan.cache.event.WeeklyPlanSlotConfirmedEvent;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class WeeklyPlanService {

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyPlanSlotRepository slotRepository;
    private final WeeklyPlanSlotStudentRepository slotStudentRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeCookingAuditRepository cookingAuditRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final ProductBatchRepository batchRepository;
    private final StockLedgerService stockLedgerService;
    private final WeeklyPlanStockReservationService reservationService;
    private final WeeklyPlanMapper wrapperMapper;
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;
    private final PersistentNotificationService persistentNotificationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WeeklyPlanPdfService weeklyPlanPdfService;

    @Transactional(readOnly = true)
    public PdfReportResponseDTO generatePlanPdf(Long planId, String orientation) {
        boolean isVertical = "vertical".equalsIgnoreCase(orientation);
        WeeklyPlanResponseDTO plan = getPlanById(planId);
        byte[] pdfBytes = weeklyPlanPdfService.generateWeeklyPlanPdf(plan, isVertical);
        String filename = weeklyPlanPdfService.generateFilename(plan);
        return new PdfReportResponseDTO(pdfBytes, filename);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "CREATE", idFromArg = -2,
            affectedDomains = {"weekly_plan"})
    public WeeklyPlanResponseDTO createPlan(WeeklyPlanRequestDTO request) {
        User currentUser = securityContextHelper.getCurrentUser();
        User chef = determineChef(request.getChefId(), currentUser);
        Integer chefId = chef.getId();

        if (request.getWeekStartDate().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_MUST_START_MONDAY));
        }

        LocalDate currentWeekStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (request.getWeekStartDate().isBefore(currentWeekStart)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_PAST_WEEK));
        }

        boolean exists = weeklyPlanRepository.findByChefIdAndWeekStartDate(chefId, request.getWeekStartDate())
                .isPresent();

        if (exists) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ALREADY_EXISTS));
        }

        WeeklyPlan plan = WeeklyPlan.builder()
                .chef(chef)
                .weekStartDate(request.getWeekStartDate())
                .weekEndDate(request.getWeekStartDate().plusDays(6))
                .status(WeeklyPlanStatus.DRAFT)
                .build();

        List<WeeklyPlanSlot> slots = mapSlots(request.getSlots(), plan, chefId);
        plan.getSlots().addAll(slots);

        WeeklyPlan savedPlan = weeklyPlanRepository.save(plan);
        persistentNotificationService.notifyPlanCreated(savedPlan);
        return wrapperMapper.toResponseDTO(savedPlan);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"weekly_plan"})
    public WeeklyPlanResponseDTO updatePlan(Long planId, WeeklyPlanRequestDTO request) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.DRAFT && plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ONLY_DRAFT));
        }

        // mapSlots ya valida solapamientos entre nuevos slots y con los CONFIRMADOS.
        // Pero mapSlots devuelve una LISTA DE NUEVOS SLOTS (PENDING).
        // Los CONFIRMADOS ya están en plan.getSlots().
        List<WeeklyPlanSlot> slotsFromRequest = mapSlots(request.getSlots(), plan, plan.getChef().getId());
        
        // Eliminar todos los slots que NO estén confirmados
        plan.getSlots().removeIf(slot -> slot.getStatus() != WeeklyPlanSlotStatus.CONFIRMED);
        
        // Añadir los nuevos slots (mapSlots ya garantizó que no hay solapamientos)
        plan.getSlots().addAll(slotsFromRequest);
        
        weeklyPlanRepository.saveAndFlush(plan);

        if (plan.getStatus() == WeeklyPlanStatus.ACTIVE || plan.getStatus() == WeeklyPlanStatus.IN_PROGRESS) {
            reservationService.validateStockForPlanUpdate(plan, slotsFromRequest);
        }

        return wrapperMapper.toResponseDTO(plan);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "STATUS_CHANGE", idFromArg = 0,
            affectedDomains = {"weekly_plan"})
    public WeeklyPlanResponseDTO activatePlan(Long planId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.DRAFT) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ONLY_DRAFT));
        }

        reservationService.validateStockForPlanActivation(plan.getId());
        plan.setStatus(WeeklyPlanStatus.ACTIVE);

        WeeklyPlan savedPlan = weeklyPlanRepository.save(plan);
        persistentNotificationService.notifyPlanActivated(savedPlan);
        return wrapperMapper.toResponseDTO(savedPlan);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "STATUS_CHANGE", idFromArg = 0,
            affectedDomains = {"weekly_plan"})
    public WeeklyPlanResponseDTO deactivatePlan(Long planId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        boolean hasConfirmedSlots = plan.getSlots().stream()
                .anyMatch(slot -> slot.getStatus() == WeeklyPlanSlotStatus.CONFIRMED);
        if (hasConfirmedSlots) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_HAS_CONFIRMED_SLOTS));
        }

        plan.setStatus(WeeklyPlanStatus.DRAFT);
        WeeklyPlan savedPlan = weeklyPlanRepository.save(plan);
        return wrapperMapper.toResponseDTO(savedPlan);
    }

    @PredictorTrigger(action = "WEEKLY_PLAN_CONFIRM")
    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "CONFIRM", idFromArg = -2,
            affectedDomains = {"weekly_plan", "ledger", "product", "stock_alerts", "recipe"})
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public WeeklyPlanSlotResponseDTO confirmSlot(Long planId, Long slotId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));
        if (!slot.getWeeklyPlan().getId().equals(planId))
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        if (slot.getStatus() != WeeklyPlanSlotStatus.PENDING && slot.getStatus() != WeeklyPlanSlotStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_PENDING));
        }

        User currentUser = securityContextHelper.getCurrentUser();
        String correlationId = UUID.randomUUID().toString();
        slot.setCorrelationId(correlationId);

        List<WeeklyPlanSlotStudent> confirmedStudents = slot.getStudents().stream()
                .filter(s -> s.getStatus() == StudentSlotStatus.ASSIGNED)
                .toList();

        List<String> studentNames = confirmedStudents.stream().map(s -> s.getStudent().getName()).toList();
        String desc = i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_WEEKLY_PLAN,
                new Object[] { String.join(", ", studentNames), slot.getRecipe().getName(), slot.getQuantity() });

        validateStockForConfirmation(plan, List.of(slot));

        List<BatchMovementItem> movements = new ArrayList<>();
        for (RecipeComponent rc : slot.getRecipe().getComponents()) {
            BigDecimal totalQty = calculateGrossQuantity(rc, slot);
            movements.add(new BatchMovementItem(
                    rc.getProduct().getId(),
                    totalQty.negate(),
                    MovementType.SALIDA,
                    desc,
                    null,
                correlationId));
        }
        stockLedgerService.recordBatchStockMovements(movements, plan.getChef(), null, false);

        slot.setStatus(WeeklyPlanSlotStatus.CONFIRMED);
        slot.setConfirmedAt(java.time.LocalDateTime.now());
        slot.setConfirmedBy(currentUser);

        if (plan.getStatus() == WeeklyPlanStatus.ACTIVE) {
            plan.setStatus(WeeklyPlanStatus.IN_PROGRESS);
        }

        boolean allCompletedOrCancelled = plan.getSlots().stream()
                .allMatch(s -> s.getStatus() == WeeklyPlanSlotStatus.CONFIRMED
                        || s.getStatus() == WeeklyPlanSlotStatus.CANCELLED);

        if (allCompletedOrCancelled) {
            plan.setStatus(WeeklyPlanStatus.COMPLETED);
        }

        weeklyPlanRepository.saveAndFlush(plan);

        persistentNotificationService.notifySlotConfirmed(plan, slot);
        if (plan.getStatus() == WeeklyPlanStatus.COMPLETED) {
            persistentNotificationService.notifyPlanCompleted(plan);
        }

        confirmedStudents.forEach(s -> s.setStatus(StudentSlotStatus.CONFIRMED));

        RecipeCookingAudit audit = RecipeCookingAudit.builder()
                .recipe(slot.getRecipe())
                .quantityCooked(slot.getQuantity())
                .portionsProduced(slot.getQuantity())
                .correlationId(correlationId)
                .details(desc)
            .componentsState(buildComponentsState(slot.getRecipe()))
                .build();
        cookingAuditRepository.saveAndFlush(audit);
        Set<Integer> affectedProductIds = slot.getRecipe().getComponents().stream()
            .map(component -> component.getProduct().getId())
            .collect(Collectors.toSet());
        applicationEventPublisher.publishEvent(new WeeklyPlanSlotConfirmedEvent(affectedProductIds));
        return wrapperMapper.toSlotResponseDTO(slotRepository.saveAndFlush(slot));
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "REVERT", idFromArg = -2,
            affectedDomains = {"weekly_plan", "ledger", "product", "stock_alerts", "recipe"})
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public WeeklyPlanSlotResponseDTO unconfirmSlot(Long planId, Long slotId) {

        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));

        if (!slot.getWeeklyPlan().getId().equals(planId)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));
        }

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE
                && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS
                && plan.getStatus() != WeeklyPlanStatus.COMPLETED) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        if (slot.getStatus() != WeeklyPlanSlotStatus.CONFIRMED) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_CONFIRMED));
        }

        String correlationId = slot.getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            revertMovementIfNeeded(correlationId, "Reverting confirmation for slot " + slot.getId());
            cookingAuditRepository.findByCorrelationId(correlationId).ifPresent(cookingAuditRepository::delete);
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
        } else if (plan.getStatus() == WeeklyPlanStatus.COMPLETED || plan.getStatus() == WeeklyPlanStatus.IN_PROGRESS) {
            plan.setStatus(WeeklyPlanStatus.ACTIVE);
        }

        weeklyPlanRepository.saveAndFlush(plan);
        return wrapperMapper.toSlotResponseDTO(slotRepository.saveAndFlush(slot));
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "REVERT", idFromArg = -2,
            affectedDomains = {"weekly_plan", "ledger", "product", "stock_alerts", "recipe"})
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public ConfirmDayResponseDTO unconfirmDay(Long planId, Integer dayOfWeek) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE
                && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS
                && plan.getStatus() != WeeklyPlanStatus.COMPLETED) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        List<WeeklyPlanSlot> daySlots = plan.getSlots().stream()
                .filter(s -> s.getDayOfWeek().equals(dayOfWeek) && s.getStatus() == WeeklyPlanSlotStatus.CONFIRMED)
                .collect(Collectors.toList());

        if (daySlots.isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_DAY_NOT_CONFIRMED));
        }

        for (WeeklyPlanSlot slot : daySlots) {
            String correlationId = slot.getCorrelationId();
            if (correlationId != null && !correlationId.isBlank()) {
                revertMovementIfNeeded(correlationId, "Reversion for full day " + dayOfWeek + " [Plan " + planId + "]");
                cookingAuditRepository.findByCorrelationId(correlationId).ifPresent(cookingAuditRepository::delete);
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
            slotRepository.save(slot);
        }

        boolean hasConfirmed = plan.getSlots().stream().anyMatch(s -> s.getStatus() == WeeklyPlanSlotStatus.CONFIRMED);
        if (hasConfirmed) {
            plan.setStatus(WeeklyPlanStatus.IN_PROGRESS);
        } else {
            plan.setStatus(WeeklyPlanStatus.ACTIVE);
        }

        weeklyPlanRepository.saveAndFlush(plan);

        return ConfirmDayResponseDTO.builder()
                .planId(planId)
                .dayOfWeek(dayOfWeek)
                .planStatus(plan.getStatus())
                .totalSlotsConfirmed(0)
                .build();
    }

    private void revertMovementIfNeeded(String correlationId, String reason) {
        String reversalCorrelationId = "REV-" + correlationId;
        boolean alreadyReverted = !stockLedgerRepository.findByCorrelationId(reversalCorrelationId).isEmpty();

        if (alreadyReverted) {
            log.info("La reversión ya estaba aplicada para correlationId={}, se continúa con desconfirmación del slot", correlationId);
            return;
        }

        stockLedgerService.revertMovement(correlationId, reason);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "CONFIRM", idFromArg = -2,
            affectedDomains = {"weekly_plan", "ledger", "product", "stock_alerts", "recipe"})
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public ConfirmDayResponseDTO confirmDay(Long planId, Integer dayOfWeek) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        List<WeeklyPlanSlot> slotsToConfirm = plan.getSlots().stream()
                .filter(s -> s.getDayOfWeek().equals(dayOfWeek) && 
                        (s.getStatus() == WeeklyPlanSlotStatus.PENDING || s.getStatus() == WeeklyPlanSlotStatus.IN_PROGRESS))
                .toList();

        if (slotsToConfirm.isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NO_PENDING_SLOTS_FOR_DAY, new Object[]{dayOfWeek}));
        }

        List<BatchMovementItem> allMovements = new ArrayList<>();
        List<RecipeCookingAudit> audits = new ArrayList<>();
        User currentUser = securityContextHelper.getCurrentUser();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        validateStockForConfirmation(plan, slotsToConfirm);

        for (WeeklyPlanSlot slot : slotsToConfirm) {
            String correlationId = UUID.randomUUID().toString();
            slot.setCorrelationId(correlationId);

            List<WeeklyPlanSlotStudent> confirmedStudents = slot.getStudents().stream()
                    .filter(s -> s.getStatus() == StudentSlotStatus.ASSIGNED)
                    .toList();

            List<String> studentNames = confirmedStudents.stream().map(s -> s.getStudent().getName()).toList();
            String desc = i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_WEEKLY_PLAN,
                    new Object[] { String.join(", ", studentNames), slot.getRecipe().getName(), slot.getQuantity() });

            for (RecipeComponent rc : slot.getRecipe().getComponents()) {
                BigDecimal totalQty = calculateGrossQuantity(rc, slot);
                allMovements.add(new BatchMovementItem(
                        rc.getProduct().getId(),
                        totalQty.negate(),
                        MovementType.SALIDA,
                        desc,
                        null,
                        correlationId
                ));
            }

            slot.setStatus(WeeklyPlanSlotStatus.CONFIRMED);
            slot.setConfirmedAt(now);
            slot.setConfirmedBy(currentUser);

            confirmedStudents.forEach(s -> s.setStatus(StudentSlotStatus.CONFIRMED));

            RecipeCookingAudit audit = RecipeCookingAudit.builder()
                    .recipe(slot.getRecipe())
                    .quantityCooked(slot.getQuantity())
                    .portionsProduced(slot.getQuantity())
                    .correlationId(correlationId)
                    .details(desc)
                    .componentsState(buildComponentsState(slot.getRecipe()))
                    .build();
            audits.add(audit);
        }

        stockLedgerService.recordBatchStockMovements(allMovements, plan.getChef(), null, false);
        cookingAuditRepository.saveAllAndFlush(audits);

        if (plan.getStatus() == WeeklyPlanStatus.ACTIVE) {
            plan.setStatus(WeeklyPlanStatus.IN_PROGRESS);
        }

        boolean allCompletedOrCancelled = plan.getSlots().stream()
                .allMatch(s -> s.getStatus() == WeeklyPlanSlotStatus.CONFIRMED || s.getStatus() == WeeklyPlanSlotStatus.CANCELLED);
        
        if (allCompletedOrCancelled) {
            plan.setStatus(WeeklyPlanStatus.COMPLETED);
        }

        weeklyPlanRepository.saveAndFlush(plan);

        persistentNotificationService.notifyDayConfirmed(plan, dayOfWeek);
        if (plan.getStatus() == WeeklyPlanStatus.COMPLETED) {
            persistentNotificationService.notifyPlanCompleted(plan);
        }

        List<WeeklyPlanSlotResponseDTO> confirmedSlotsDTO = slotsToConfirm.stream()
                .map(wrapperMapper::toSlotResponseDTO)
                .toList();

        Set<Integer> affectedProductIds = slotsToConfirm.stream()
            .flatMap(slot -> slot.getRecipe().getComponents().stream())
            .map(component -> component.getProduct().getId())
            .collect(Collectors.toSet());
        applicationEventPublisher.publishEvent(new WeeklyPlanSlotConfirmedEvent(affectedProductIds));

        return ConfirmDayResponseDTO.builder()
                .planId(plan.getId())
                .dayOfWeek(dayOfWeek)
                .planStatus(plan.getStatus())
                .confirmedSlots(confirmedSlotsDTO)
                .totalSlotsConfirmed(confirmedSlotsDTO.size())
                .build();
    }

            private void validateStockForConfirmation(WeeklyPlan plan, List<WeeklyPlanSlot> slotsToConfirm) {
            Map<Integer, BigDecimal> requiredByProduct = new HashMap<>();
            Map<Integer, LocalDate> requiredDateByProduct = new HashMap<>();

            for (WeeklyPlanSlot slot : slotsToConfirm) {
                BigDecimal portions = slot.getRecipe().getPortions() == null || slot.getRecipe().getPortions().compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ONE
                    : slot.getRecipe().getPortions();

                for (RecipeComponent rc : slot.getRecipe().getComponents()) {
                BigDecimal netQty = rc.getQuantity().multiply(slot.getQuantity())
                    .divide(portions, 4, RoundingMode.HALF_UP);
                BigDecimal availabilityPercent = rc.getProduct().getAvailabilityPercentage() != null
                    ? rc.getProduct().getAvailabilityPercentage()
                    : BigDecimal.valueOf(100.00);
                BigDecimal grossQty = availabilityPercent.compareTo(BigDecimal.ZERO) > 0
                    ? netQty.multiply(BigDecimal.valueOf(100)).divide(availabilityPercent, 3, RoundingMode.HALF_UP)
                    : netQty;
                Integer productId = rc.getProduct().getId();
                requiredByProduct.merge(productId, grossQty, BigDecimal::add);

                int dayOffset = Math.max(0, slot.getDayOfWeek() - 1);
                LocalDate slotDate = plan.getWeekStartDate().plusDays(dayOffset);
                requiredDateByProduct.merge(productId, slotDate,
                    (current, candidate) -> candidate.isAfter(current) ? candidate : current);
                }
            }

            for (Map.Entry<Integer, BigDecimal> entry : requiredByProduct.entrySet()) {
                LocalDate targetDate = requiredDateByProduct.getOrDefault(entry.getKey(), plan.getWeekStartDate());
                BigDecimal available = batchRepository.sumNonExpiredRemainingQuantity(entry.getKey(), targetDate);
                if (available.compareTo(entry.getValue()) < 0) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_BATCH_INSUFFICIENT_STOCK));
                }
            }
            }

            private BigDecimal calculateGrossQuantity(RecipeComponent rc, WeeklyPlanSlot slot) {
            BigDecimal portions = slot.getRecipe().getPortions() == null || slot.getRecipe().getPortions().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE
                : slot.getRecipe().getPortions();
            BigDecimal netQty = rc.getQuantity().multiply(slot.getQuantity())
                .divide(portions, 4, RoundingMode.HALF_UP);
            BigDecimal availabilityPercent = rc.getProduct().getAvailabilityPercentage() != null
                ? rc.getProduct().getAvailabilityPercentage()
                : BigDecimal.valueOf(100.00);
            return availabilityPercent.compareTo(BigDecimal.ZERO) > 0
                ? netQty.multiply(BigDecimal.valueOf(100)).divide(availabilityPercent, 3, RoundingMode.HALF_UP)
                : netQty;
            }

    @Cacheable(value = "weekly_plan", key = "#planId")
    @Transactional(readOnly = true)
    public WeeklyPlanResponseDTO getPlanById(Long planId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);
        return wrapperMapper.toResponseDTO(plan);
    }

    @Transactional(readOnly = true)
    public Page<WeeklyPlanResponseDTO> getAllPlans(Pageable pageable) {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return weeklyPlanRepository.findAll(pageable).map(wrapperMapper::toResponseDTO);
        } else {
            Integer chefId;
            if (currentUser.getRole() == Role.ELEVATED) {
                if (currentUser.getTeacher() == null)
                    throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ELEVATED_REQUIRE_TEACHER));
                chefId = currentUser.getTeacher().getId();
            } else {
                chefId = currentUser.getId();
            }
            return weeklyPlanRepository.findAllByChefId(chefId, pageable).map(wrapperMapper::toResponseDTO);
        }
    }

    @Transactional(readOnly = true)
    public WeeklyPlanResponseDTO getCurrentWeekPlan() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ADMIN_NO_CURRENT_PLAN));
        }
        Integer chefId;
        if (currentUser.getRole() == Role.ELEVATED) {
            if (currentUser.getTeacher() == null)
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ELEVATED_REQUIRE_TEACHER));
            chefId = currentUser.getTeacher().getId();
        } else {
            chefId = currentUser.getId();
        }
        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        WeeklyPlan plan = weeklyPlanRepository
                .findByChefIdAndWeekStartDateAndStatusIn(chefId, weekStart, List.of(WeeklyPlanStatus.ACTIVE, WeeklyPlanStatus.IN_PROGRESS))
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        return wrapperMapper.toResponseDTO(plan);
    }

    @Transactional(readOnly = true)
    public List<WeeklyPlanStockRequirementDTO> getStockRequirements(Long planId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);
        return reservationService.getStockRequirements(planId);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"weekly_plan"})
    public WeeklyPlanSlotResponseDTO cancelSlot(Long planId, Long slotId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));
        if (!slot.getWeeklyPlan().getId().equals(planId))
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        if (slot.getStatus() == WeeklyPlanSlotStatus.CONFIRMED || slot.getStatus() == WeeklyPlanSlotStatus.CANCELLED) {
            throw new InvalidOperationException(
                    i18nService.getMessage(slot.getStatus() == WeeklyPlanSlotStatus.CONFIRMED 
                            ? MessageKey.ERROR_WEEKLY_PLAN_SLOT_ALREADY_CONFIRMED 
                            : MessageKey.ERROR_WEEKLY_PLAN_SLOT_ALREADY_CANCELLED));
        }

        slot.setStatus(WeeklyPlanSlotStatus.CANCELLED);
        slot.getStudents().forEach(s -> s.setStatus(StudentSlotStatus.CANCELLED));

        return wrapperMapper.toSlotResponseDTO(slotRepository.save(slot));
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"weekly_plan"})
    public WeeklyPlanSlotResponseDTO restoreSlot(Long planId, Long slotId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE
                && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS
                && plan.getStatus() != WeeklyPlanStatus.COMPLETED) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));
        if (!slot.getWeeklyPlan().getId().equals(planId)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));
        }

        if (slot.getStatus() != WeeklyPlanSlotStatus.CANCELLED) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_CANCELLED));
        }

        slot.setStatus(WeeklyPlanSlotStatus.PENDING);
        slot.getStudents().forEach(studentSlot -> {
            if (studentSlot.getStatus() == StudentSlotStatus.CANCELLED) {
                studentSlot.setStatus(StudentSlotStatus.ASSIGNED);
                studentSlot.setCancelledAt(null);
                studentSlot.setCancelledBy(null);
            }
        });

        boolean hasConfirmedSlots = plan.getSlots().stream()
                .anyMatch(s -> s.getStatus() == WeeklyPlanSlotStatus.CONFIRMED);
        if (plan.getStatus() == WeeklyPlanStatus.COMPLETED) {
            plan.setStatus(hasConfirmedSlots ? WeeklyPlanStatus.IN_PROGRESS : WeeklyPlanStatus.ACTIVE);
        }

        weeklyPlanRepository.saveAndFlush(plan);
        return wrapperMapper.toSlotResponseDTO(slotRepository.saveAndFlush(slot));
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"weekly_plan"})
    public WeeklyPlanSlotStudentResponseDTO cancelStudentFromSlot(Long planId, Long slotId, Integer studentId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));
        if (!slot.getWeeklyPlan().getId().equals(planId))
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));

        WeeklyPlanSlotStudent studentSlot = slot.getStudents().stream()
                .filter(s -> s.getStudent().getId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_STUDENT_NOT_IN_SLOT)));

        if (studentSlot.getStatus() == StudentSlotStatus.CANCELLED) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_ALREADY_CANCELLED));
        }

        studentSlot.setStatus(StudentSlotStatus.CANCELLED);
        studentSlot.setCancelledAt(java.time.LocalDateTime.now());
        studentSlot.setCancelledBy(securityContextHelper.getCurrentUser());

        slotRepository.save(slot);
        return wrapperMapper.toStudentResponseDTO(studentSlot);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"weekly_plan"})
    public WeeklyPlanSlotStudentResponseDTO restoreStudentInSlot(Long planId, Long slotId, Integer studentId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));
        if (!slot.getWeeklyPlan().getId().equals(planId)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));
        }

        WeeklyPlanSlotStudent studentSlot = slot.getStudents().stream()
                .filter(s -> s.getStudent().getId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_STUDENT_NOT_IN_SLOT)));

        if (studentSlot.getStatus() != StudentSlotStatus.CANCELLED) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_STUDENT_NOT_CANCELLED));
        }

        studentSlot.setStatus(StudentSlotStatus.ASSIGNED);
        studentSlot.setCancelledAt(null);
        studentSlot.setCancelledBy(null);

        slotRepository.saveAndFlush(slot);
        return wrapperMapper.toStudentResponseDTO(studentSlot);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"weekly_plan"})
    @Transactional(rollbackFor = Exception.class)
    public void cancelStudentFromDay(Long planId, Integer dayOfWeek, Integer studentId) {
        log.info("Cancelling student {} from plan {} for day {}", studentId, planId, dayOfWeek);
        
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        boolean changed = false;
        User currentUser = securityContextHelper.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        for (WeeklyPlanSlot slot : plan.getSlots()) {
            if (slot.getDayOfWeek().equals(dayOfWeek) && slot.getStatus() != WeeklyPlanSlotStatus.CANCELLED) {
                for (WeeklyPlanSlotStudent studentSlot : slot.getStudents()) {
                    if (studentSlot.getStudent().getId().equals(studentId)
                            && studentSlot.getStatus() != StudentSlotStatus.CANCELLED) {
                        
                        studentSlot.setStatus(StudentSlotStatus.CANCELLED);
                        studentSlot.setCancelledAt(now);
                        studentSlot.setCancelledBy(currentUser);
                        changed = true;
                        
                        log.debug("Student {} cancelled from slot {}", studentId, slot.getId());
                    }
                }
            }
        }
        
        if (changed) {
            weeklyPlanRepository.saveAndFlush(plan);
            log.info("Student {} attendance updated and plan {} saved/flushed", studentId, planId);
        } else {
            log.info("No modifications needed for student {} in plan {} for day {}", studentId, planId, dayOfWeek);
        }
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"weekly_plan"})
    @Transactional(rollbackFor = Exception.class)
    public void restoreStudentFromDay(Long planId, Integer dayOfWeek, Integer studentId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        boolean changed = false;
        for (WeeklyPlanSlot slot : plan.getSlots()) {
            if (slot.getDayOfWeek().equals(dayOfWeek) && slot.getStatus() != WeeklyPlanSlotStatus.CANCELLED) {
                for (WeeklyPlanSlotStudent studentSlot : slot.getStudents()) {
                    if (studentSlot.getStudent().getId().equals(studentId)
                            && studentSlot.getStatus() == StudentSlotStatus.CANCELLED) {
                        studentSlot.setStatus(StudentSlotStatus.ASSIGNED);
                        studentSlot.setCancelledAt(null);
                        studentSlot.setCancelledBy(null);
                        changed = true;
                    }
                }
            }
        }

        if (!changed) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NO_CANCELLATIONS_FOR_DAY));
        }

        weeklyPlanRepository.saveAndFlush(plan);
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @RealtimeSync(entityType = "weekly_plan", action = "REVERT", idFromArg = -2,
            affectedDomains = {"weekly_plan"})
    @Transactional(rollbackFor = Exception.class)
    public void restoreDay(Long planId, Integer dayOfWeek) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE
                && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS
                && plan.getStatus() != WeeklyPlanStatus.COMPLETED) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        boolean changed = false;
        for (WeeklyPlanSlot slot : plan.getSlots()) {
            if (slot.getDayOfWeek().equals(dayOfWeek) && slot.getStatus() == WeeklyPlanSlotStatus.CANCELLED) {
                slot.setStatus(WeeklyPlanSlotStatus.PENDING);
                slot.getStudents().forEach(studentSlot -> {
                    if (studentSlot.getStatus() == StudentSlotStatus.CANCELLED) {
                        studentSlot.setStatus(StudentSlotStatus.ASSIGNED);
                        studentSlot.setCancelledAt(null);
                        studentSlot.setCancelledBy(null);
                    }
                });
                changed = true;
            }
        }

        if (!changed) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NO_CANCELLED_SLOTS_FOR_DAY));
        }

        boolean hasConfirmedSlots = plan.getSlots().stream()
                .anyMatch(s -> s.getStatus() == WeeklyPlanSlotStatus.CONFIRMED);
        if (plan.getStatus() == WeeklyPlanStatus.COMPLETED) {
            plan.setStatus(hasConfirmedSlots ? WeeklyPlanStatus.IN_PROGRESS : WeeklyPlanStatus.ACTIVE);
        }

        weeklyPlanRepository.saveAndFlush(plan);
    }

    @Cacheable(value = "student_metrics", key = "#chefId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<StudentMetricsResponseDTO> getStudentMetrics(Integer chefId, Pageable pageable) {
        User currentUser = securityContextHelper.getCurrentUser();
        Pageable normalizedPageable = normalizeStudentMetricsPageable(pageable);

        // Security: Default to current user's chef ID for non-ADMIN roles if null or
        // different
        if (currentUser.getRole() != Role.ADMIN) {
            Integer authorizedChefId = currentUser.getRole() == Role.ELEVATED
                    ? (currentUser.getTeacher() != null ? currentUser.getTeacher().getId() : null)
                    : currentUser.getId();

            if (chefId == null || !chefId.equals(authorizedChefId)) {
                chefId = authorizedChefId;
            }
        }

        if (chefId != null) {
            Page<Object[]> results = slotStudentRepository.findStudentMetricsByChefId(chefId, normalizedPageable);
            return results.map(row -> new StudentMetricsResponseDTO((Integer) row[0], (String) row[1], (Long) row[2],
                    (Long) row[3], (Long) row[4],
                    ((Long) row[2] > 0) ? ((Long) row[3] * 100.0 / (Long) row[2]) : 0.0));
        } else {
            // Only ADMIN can reach here if chefId is still null
            Page<Object[]> results = slotStudentRepository.findAllStudentMetrics(normalizedPageable);
            return results.map(row -> new StudentMetricsResponseDTO((Integer) row[0], (String) row[1], (Long) row[2],
                    (Long) row[3], (Long) row[4],
                    ((Long) row[2] > 0) ? ((Long) row[3] * 100.0 / (Long) row[2]) : 0.0));
        }
    }

    private Pageable normalizeStudentMetricsPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Order.asc("student.name")));
        }

        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Order.asc("student.name")));
        }

        List<Sort.Order> mappedOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String mappedProperty = switch (order.getProperty()) {
                case "studentName", "name", "student.name" -> "student.name";
                case "studentId", "id", "student.id" -> "student.id";
                case "status" -> "status";
                default -> "student.name";
            };
            mappedOrders.add(new Sort.Order(order.getDirection(), mappedProperty));
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(mappedOrders));
    }

    private String buildComponentsState(Recipe recipe) {
        try {
            if (recipe.getComponents() == null || recipe.getComponents().isEmpty())
                return "{}";

            List<Map<String, Object>> components = recipe.getComponents().stream()
                    .map(comp -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("productId", comp.getProduct().getId());
                        data.put("productName", comp.getProduct().getName());
                        data.put("quantity", comp.getQuantity());
                        return data;
                    }).collect(Collectors.toList());

            return objectMapper.writeValueAsString(Map.of("components", components));
        } catch (Exception e) {
            log.warn("Error building componentsState for recipe {}: {}", recipe.getId(), e.getMessage());
            return "{}";
        }
    }

    private User determineChef(Integer requestedChefId, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            if (requestedChefId == null)
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ADMIN_REQUIRE_CHEF));
            User chef = userRepository.findById(requestedChefId).orElseThrow(() -> new ResourceNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_CHEF_NOT_FOUND)));
            if (chef.getRole() != Role.CHEF)
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_USER_NOT_CHEF));
            return chef;
        }
        if (currentUser.getRole() == Role.ELEVATED) {
            if (currentUser.getTeacher() == null)
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ELEVATED_REQUIRE_TEACHER));
            return currentUser.getTeacher();
        }
        return currentUser;
    }

    private void checkPermissions(WeeklyPlan plan) {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN)
            return;
        if (currentUser.getRole() == Role.ELEVATED) {
            if (currentUser.getTeacher() == null || !currentUser.getTeacher().getId().equals(plan.getChef().getId())) {
                throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NO_PERMISSION));
            }
        } else if (!plan.getChef().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NO_PERMISSION));
        }
    }

    private List<WeeklyPlanSlot> mapSlots(List<WeeklyPlanSlotRequestDTO> dtos, WeeklyPlan plan, Integer chefId) {
        List<UserProjection> students = userRepository.findProjectedByTeacherIdAndIsHiddenFalse(chefId);
        Set<Integer> validIds = students.stream().map(UserProjection::getId).collect(Collectors.toSet());

        Set<Integer> recipeIds = dtos.stream().map(WeeklyPlanSlotRequestDTO::getRecipeId).collect(Collectors.toSet());
        Map<Integer, Recipe> recipeMap = recipeRepository.findAllById(recipeIds).stream()
                .collect(Collectors.toMap(Recipe::getId, r -> r));

        Set<Integer> allStudentIds = dtos.stream().filter(d -> d.getStudentIds() != null)
                .flatMap(d -> d.getStudentIds().stream()).collect(Collectors.toSet());
        Map<Integer, User> studentMap = userRepository.findAllById(allStudentIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // existingConfirmedMap para rastrear slots que NO deben ser recreados
        Map<String, WeeklyPlanSlot> existingConfirmedMap = plan.getSlots().stream()
                .filter(s -> s.getStatus() == WeeklyPlanSlotStatus.CONFIRMED)
                .collect(Collectors.toMap(
                        s -> s.getRecipe().getId() + "-" + s.getDayOfWeek() + "-" + s.getStartTime() + "-" + s.getEndTime(),
                        s -> s,
                        (s1, s2) -> s1
                ));

        List<WeeklyPlanSlot> newSlots = new ArrayList<>();

        for (WeeklyPlanSlotRequestDTO dto : dtos) {
            String slotKey = dto.getRecipeId() + "-" + dto.getDayOfWeek() + "-" + dto.getStartTime() + "-" + dto.getEndTime();
            
            // Si el slot ya está CONFIRMADO en el plan, no creamos uno nuevo
            if (existingConfirmedMap.containsKey(slotKey)) {
                continue;
            }

            Set<Integer> studentIdsInSlot = new java.util.HashSet<>();
            
            // Validar solapamientos con slots nuevos que estamos añadiendo
            for (WeeklyPlanSlot existing : newSlots) {
                if (existing.getDayOfWeek().equals(dto.getDayOfWeek())) {
                    if (dto.getStartTime().isBefore(existing.getEndTime())
                            && dto.getEndTime().isAfter(existing.getStartTime())) {
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_OVERLAP, new Object[]{dto.getDayOfWeek()}));
                    }
                }
            }

            // Validar solapamientos con slots existentes CONFIRMADOS en el plan
            for (WeeklyPlanSlot existing : plan.getSlots()) {
                if (existing.getStatus() == WeeklyPlanSlotStatus.CONFIRMED && existing.getDayOfWeek().equals(dto.getDayOfWeek())) {
                    if (dto.getStartTime().isBefore(existing.getEndTime())
                            && dto.getEndTime().isAfter(existing.getStartTime())) {
                        
                        String existingKey = existing.getRecipe().getId() + "-" + existing.getDayOfWeek() + "-" + existing.getStartTime() + "-" + existing.getEndTime();
                        if (slotKey.equals(existingKey)) {
                            continue;
                        }

                        // Si el slot en el DTO es idéntico al CONFIRMADO (mismas horas, receta, etc), no es un solapamiento real, es el mismo slot.
                        // Pero como no lo encontramos en existingConfirmedMap, significa que algo cambió.
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_OVERLAP_EXISTING, new Object[]{dto.getDayOfWeek()}));
                    }
                }
            }

            Recipe recipe = recipeMap.get(dto.getRecipeId());
            if (recipe == null)
                throw new InvalidOperationException(i18nService
                        .getMessage(MessageKey.ERROR_WEEKLY_PLAN_RECIPE_NOT_FOUND, new Object[] { dto.getRecipeId() }));

            WeeklyPlanSlot slot = WeeklyPlanSlot.builder()
                    .weeklyPlan(plan)
                    .recipe(recipe)
                    .quantity(dto.getQuantity())
                    .dayOfWeek(dto.getDayOfWeek())
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .sortOrder(dto.getSortOrder())
                    .status(WeeklyPlanSlotStatus.PENDING)
                    .build();

            if (dto.getStudentIds() != null) {
                for (Integer sid : dto.getStudentIds()) {
                    if (!validIds.contains(sid))
                        throw new InvalidOperationException(i18nService
                                .getMessage(MessageKey.ERROR_WEEKLY_PLAN_STUDENT_INVALID, new Object[] { sid }));


                    if (!studentIdsInSlot.add(sid)) {
                        log.warn("Student {} added twice in slot", sid);
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_STUDENT_DUPLICATE_IN_SLOT, new Object[]{sid}));
                    }

                    User studentRef = studentMap.get(sid);
                    if (studentRef == null)
                        studentRef = userRepository.getReferenceById(sid);
                    WeeklyPlanSlotStudent wss = WeeklyPlanSlotStudent.builder()
                            .slot(slot)
                            .student(studentRef)
                            .status(StudentSlotStatus.ASSIGNED)
                            .build();
                    slot.getStudents().add(wss);
                }
            }
            newSlots.add(slot);
        }
        return newSlots;
    }

    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void deleteAllPlans() {
        log.warn("ADMIN action: Deleting all weekly plans and related notifications");
        
        // Clean up notifications related to weekly plans
        List<NotificationType> wpTypes = List.of(
            NotificationType.WEEKLY_PLAN_CREATED,
            NotificationType.WEEKLY_PLAN_ACTIVATED,
            NotificationType.WEEKLY_PLAN_SLOT_CONFIRMED,
            NotificationType.WEEKLY_PLAN_DAY_CONFIRMED,
            NotificationType.WEEKLY_PLAN_COMPLETED,
            NotificationType.WEEKLY_PLAN_CANCELLED,
            NotificationType.WEEKLY_PLAN_AUTO_CLOSED
        );
        notificationRepository.deleteByTypes(wpTypes);
        
        // Delete all weekly plans (cascades to slots and students)
        weeklyPlanRepository.deleteAll();
        
        log.info("All weekly plans and related notifications deleted successfully");
    }
}
