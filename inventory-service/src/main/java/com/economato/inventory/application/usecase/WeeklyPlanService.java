package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.WeeklyPlanRequestDTO;
import com.economato.inventory.application.dto.request.WeeklyPlanSlotRequestDTO;
import com.economato.inventory.application.dto.request.BatchMovementItem;
import com.economato.inventory.application.dto.response.StudentMetricsResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.dto.response.ConfirmDayResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.dto.projection.UserProjection;
import com.economato.inventory.application.mapper.WeeklyPlanMapper;
import com.economato.inventory.application.dto.response.WeeklyPlanStockRequirementDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotStudentResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.economato.inventory.domain.model.*;
import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.*;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class WeeklyPlanService {

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyPlanSlotRepository slotRepository;
    private final WeeklyPlanSlotStudentRepository slotStudentRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeCookingAuditRepository cookingAuditRepository;
    private final StockLedgerService stockLedgerService;
    private final WeeklyPlanStockReservationService reservationService;
    private final WeeklyPlanMapper wrapperMapper;
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    public WeeklyPlanResponseDTO createPlan(WeeklyPlanRequestDTO request) {
        User currentUser = securityContextHelper.getCurrentUser();
        User chef = determineChef(request.getChefId(), currentUser);
        Integer chefId = chef.getId();

        if (request.getWeekStartDate().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_MUST_START_MONDAY));
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

        return wrapperMapper.toResponseDTO(weeklyPlanRepository.save(plan));
    }

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

    public WeeklyPlanResponseDTO activatePlan(Long planId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.DRAFT) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ONLY_DRAFT));
        }

        reservationService.validateStockForPlanActivation(plan.getId());
        plan.setStatus(WeeklyPlanStatus.ACTIVE);

        return wrapperMapper.toResponseDTO(weeklyPlanRepository.save(plan));
    }

    @PredictorTrigger(action = "WEEKLY_PLAN_CONFIRM")
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

        for (RecipeComponent rc : slot.getRecipe().getComponents()) {
            BigDecimal totalQty = rc.getQuantity().multiply(slot.getQuantity())
                    .divide(slot.getRecipe().getPortions(), 4, RoundingMode.HALF_UP);
            stockLedgerService.recordStockMovement(
                    rc.getProduct().getId(),
                    totalQty.negate(),
                    MovementType.SALIDA,
                    desc,
                    plan.getChef(),
                    null,
                    null,
                    correlationId);
        }

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
        return wrapperMapper.toSlotResponseDTO(slotRepository.saveAndFlush(slot));
    }

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
                BigDecimal totalQty = rc.getQuantity().multiply(slot.getQuantity())
                        .divide(slot.getRecipe().getPortions(), 4, RoundingMode.HALF_UP);
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

        stockLedgerService.recordBatchStockMovements(allMovements, plan.getChef(), null);
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

        List<WeeklyPlanSlotResponseDTO> confirmedSlotsDTO = slotsToConfirm.stream()
                .map(wrapperMapper::toSlotResponseDTO)
                .toList();

        return ConfirmDayResponseDTO.builder()
                .planId(plan.getId())
                .dayOfWeek(dayOfWeek)
                .planStatus(plan.getStatus())
                .confirmedSlots(confirmedSlotsDTO)
                .totalSlotsConfirmed(confirmedSlotsDTO.size())
                .build();
    }

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

    public void cancelStudentFromDay(Long planId, Integer dayOfWeek, Integer studentId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(
                () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }

        boolean changed = false;
        User currentUser = securityContextHelper.getCurrentUser();

        for (WeeklyPlanSlot slot : plan.getSlots()) {
            if (slot.getDayOfWeek().equals(dayOfWeek) && slot.getStatus() != WeeklyPlanSlotStatus.CANCELLED) {
                for (WeeklyPlanSlotStudent studentSlot : slot.getStudents()) {
                    if (studentSlot.getStudent().getId().equals(studentId)
                            && studentSlot.getStatus() != StudentSlotStatus.CANCELLED) {
                        studentSlot.setStatus(StudentSlotStatus.CANCELLED);
                        studentSlot.setCancelledAt(java.time.LocalDateTime.now());
                        studentSlot.setCancelledBy(currentUser);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            weeklyPlanRepository.saveAndFlush(plan);
        }
    }

    @Transactional(readOnly = true)
    public Page<StudentMetricsResponseDTO> getStudentMetrics(Integer chefId, Pageable pageable) {
        User currentUser = securityContextHelper.getCurrentUser();

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
            Page<Object[]> results = slotStudentRepository.findStudentMetricsByChefId(chefId, pageable);
            return results.map(row -> new StudentMetricsResponseDTO((Integer) row[0], (String) row[1], (Long) row[2],
                    (Long) row[3], (Long) row[4],
                    ((Long) row[2] > 0) ? ((Long) row[3] * 100.0 / (Long) row[2]) : 0.0));
        } else {
            // Only ADMIN can reach here if chefId is still null
            Page<Object[]> results = slotStudentRepository.findAllStudentMetrics(pageable);
            return results.map(row -> new StudentMetricsResponseDTO((Integer) row[0], (String) row[1], (Long) row[2],
                    (Long) row[3], (Long) row[4],
                    ((Long) row[2] > 0) ? ((Long) row[3] * 100.0 / (Long) row[2]) : 0.0));
        }
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
}
