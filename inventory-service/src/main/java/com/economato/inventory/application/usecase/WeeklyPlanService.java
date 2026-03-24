package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.WeeklyPlanRequestDTO;
import com.economato.inventory.application.dto.request.WeeklyPlanSlotRequestDTO;
import com.economato.inventory.application.dto.response.StudentMetricsResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.dto.projection.UserProjection;
import com.economato.inventory.application.mapper.WeeklyPlanMapper;
import com.economato.inventory.application.dto.response.WeeklyPlanStockRequirementDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotStudentResponseDTO;
import com.economato.inventory.domain.model.*;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalTime;
import java.util.ArrayList;
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

    public WeeklyPlanResponseDTO createPlan(WeeklyPlanRequestDTO request) {
        User currentUser = securityContextHelper.getCurrentUser();
        Integer chefId = determineChefId(request.getChefId(), currentUser);
        User chef = userRepository.findById(chefId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_CHEF_NOT_FOUND)));

        if (request.getWeekStartDate().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_MUST_START_MONDAY));
        }

        boolean existsActive = weeklyPlanRepository.findByChefIdAndWeekStartDate(chefId, request.getWeekStartDate())
                .filter(p -> p.getStatus() == WeeklyPlanStatus.DRAFT || p.getStatus() == WeeklyPlanStatus.ACTIVE).isPresent();

        if (existsActive) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ALREADY_EXISTS));
        }

        WeeklyPlan plan = WeeklyPlan.builder()
                .chef(chef)
                .weekStartDate(request.getWeekStartDate())
                .weekEndDate(request.getWeekStartDate().plusDays(6))
                .status(WeeklyPlanStatus.DRAFT)
                .build();

        List<WeeklyPlanSlot> slots = mapSlots(request.getSlots(), plan, chefId, request.getWeekStartDate());
        plan.getSlots().addAll(slots);

        return wrapperMapper.toResponseDTO(weeklyPlanRepository.save(plan));
    }

    public WeeklyPlanResponseDTO updatePlan(Long planId, WeeklyPlanRequestDTO request) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.DRAFT) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ONLY_DRAFT));
        }

        plan.getSlots().removeIf(slot -> slot.getStatus() != WeeklyPlanSlotStatus.CONFIRMED);

        List<WeeklyPlanSlot> newSlots = mapSlots(request.getSlots(), plan, plan.getChef().getId(), plan.getWeekStartDate());
        plan.getSlots().addAll(newSlots);

        if (plan.getStatus() == WeeklyPlanStatus.ACTIVE) {
            reservationService.validateStockForPlanActivation(plan.getId());
        }

        return wrapperMapper.toResponseDTO(weeklyPlanRepository.save(plan));
    }

    public WeeklyPlanResponseDTO activatePlan(Long planId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        if (plan.getStatus() != WeeklyPlanStatus.DRAFT) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ONLY_DRAFT));
        }

        reservationService.validateStockForPlanActivation(plan.getId());
        plan.setStatus(WeeklyPlanStatus.ACTIVE);

        return wrapperMapper.toResponseDTO(weeklyPlanRepository.save(plan));
    }

    public WeeklyPlanSlotResponseDTO confirmSlot(Long planId, Long slotId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));
        if (!slot.getWeeklyPlan().getId().equals(planId)) throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));

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
        String desc = i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_WEEKLY_PLAN, new Object[]{String.join(", ", studentNames), slot.getRecipe().getName(), slot.getQuantity()});

        for (RecipeComponent rc : slot.getRecipe().getComponents()) {
            BigDecimal totalQty = rc.getQuantity().multiply(slot.getQuantity());
            stockLedgerService.recordStockMovement(
                    rc.getProduct().getId(),
                    totalQty.negate(),
                    MovementType.SALIDA,
                    desc,
                    plan.getChef(), 
                    null,
                    null,
                    correlationId
            );
        }

        slot.setStatus(WeeklyPlanSlotStatus.CONFIRMED);
        slot.setConfirmedAt(java.time.LocalDateTime.now());
        slot.setConfirmedBy(currentUser);

        confirmedStudents.forEach(s -> s.setStatus(StudentSlotStatus.CONFIRMED));
        
        RecipeCookingAudit audit = RecipeCookingAudit.builder()
                .recipe(slot.getRecipe())
                .quantityCooked(slot.getQuantity())
                .portionsProduced(slot.getRecipe().getPortions().multiply(slot.getQuantity()))
                .correlationId(correlationId)
                .details(desc)
                .build();
        cookingAuditRepository.save(audit);

        return wrapperMapper.toSlotResponseDTO(slotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public WeeklyPlanResponseDTO getPlanById(Long planId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);
        return wrapperMapper.toResponseDTO(plan);
    }

    @Transactional(readOnly = true)
    public Page<WeeklyPlanResponseDTO> getAllPlans(Pageable pageable) {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return weeklyPlanRepository.findAll(pageable).map(wrapperMapper::toResponseDTO);
        } else {
            Integer chefId = currentUser.getRole() == Role.ELEVATED ? currentUser.getTeacher().getId() : currentUser.getId();
            return weeklyPlanRepository.findAllByChefId(chefId, pageable).map(wrapperMapper::toResponseDTO);
        }
    }

    @Transactional(readOnly = true)
    public WeeklyPlanResponseDTO getCurrentWeekPlan() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            throw new InvalidOperationException("ADMIN no tiene plan actual por defecto.");
        }
        Integer chefId = currentUser.getRole() == Role.ELEVATED ? currentUser.getTeacher().getId() : currentUser.getId();
        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        
        WeeklyPlan plan = weeklyPlanRepository.findByChefIdAndWeekStartDateAndStatus(chefId, weekStart, WeeklyPlanStatus.ACTIVE)
            .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        return wrapperMapper.toResponseDTO(plan);
    }

    @Transactional(readOnly = true)
    public List<WeeklyPlanStockRequirementDTO> getStockRequirements(Long planId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);
        return reservationService.getStockRequirements(planId);
    }

    public WeeklyPlanSlotResponseDTO cancelSlot(Long planId, Long slotId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));
        if (!slot.getWeeklyPlan().getId().equals(planId)) throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));

        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE && plan.getStatus() != WeeklyPlanStatus.IN_PROGRESS) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_ACTIVE));
        }
        if (!slot.getWeeklyPlan().getId().equals(planId)) throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));

        if (slot.getStatus() == WeeklyPlanSlotStatus.CONFIRMED) {
            throw new InvalidOperationException("No se puede cancelar un slot ya confirmado.");
        }

        slot.setStatus(WeeklyPlanSlotStatus.CANCELLED);
        slot.getStudents().forEach(s -> s.setStatus(StudentSlotStatus.CANCELLED));
        
        return wrapperMapper.toSlotResponseDTO(slotRepository.save(slot));
    }

    public WeeklyPlanSlotStudentResponseDTO cancelStudentFromSlot(Long planId, Long slotId, Integer studentId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        WeeklyPlanSlot slot = slotRepository.findWithDetailsById(slotId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_FOUND)));
        if (!slot.getWeeklyPlan().getId().equals(planId)) throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_SLOT_NOT_BELONG));

        WeeklyPlanSlotStudent studentSlot = slot.getStudents().stream()
            .filter(s -> s.getStudent().getId().equals(studentId))
            .findFirst()
            .orElseThrow(() -> new InvalidOperationException("Alumno no pertenece a este slot"));

        studentSlot.setStatus(StudentSlotStatus.CANCELLED);
        studentSlot.setCancelledAt(java.time.LocalDateTime.now());
        studentSlot.setCancelledBy(securityContextHelper.getCurrentUser());

        slotRepository.save(slot);
        return wrapperMapper.toStudentResponseDTO(studentSlot);
    }

    public void cancelStudentFromDay(Long planId, Integer dayOfWeek, Integer studentId) {
        WeeklyPlan plan = weeklyPlanRepository.findWithDetailsById(planId).orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NOT_FOUND)));
        checkPermissions(plan);

        boolean changed = false;
        User currentUser = securityContextHelper.getCurrentUser();
        
        for (WeeklyPlanSlot slot : plan.getSlots()) {
            if (slot.getDayOfWeek().equals(dayOfWeek) && slot.getStatus() != WeeklyPlanSlotStatus.CANCELLED) {
                for (WeeklyPlanSlotStudent studentSlot : slot.getStudents()) {
                    if (studentSlot.getStudent().getId().equals(studentId) && studentSlot.getStatus() != StudentSlotStatus.CANCELLED) {
                        studentSlot.setStatus(StudentSlotStatus.CANCELLED);
                        studentSlot.setCancelledAt(java.time.LocalDateTime.now());
                        studentSlot.setCancelledBy(currentUser);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            weeklyPlanRepository.save(plan);
        }
    }

    @Transactional(readOnly = true)
    public Page<StudentMetricsResponseDTO> getStudentMetrics(Integer chefId, Pageable pageable) {
        if (chefId != null) {
            Page<Object[]> results = slotStudentRepository.findStudentMetricsByChefId(chefId, pageable);
            return results.map(row -> new StudentMetricsResponseDTO((Integer) row[0], (String) row[1], (Long) row[2], (Long) row[3], (Long) row[4], 
                ((Long) row[2] > 0) ? ((Long) row[3] * 100.0 / (Long) row[2]) : 0.0));
        } else {
            Page<Object[]> results = slotStudentRepository.findAllStudentMetrics(pageable);
            return results.map(row -> new StudentMetricsResponseDTO((Integer) row[0], (String) row[1], (Long) row[2], (Long) row[3], (Long) row[4], 
                ((Long) row[2] > 0) ? ((Long) row[3] * 100.0 / (Long) row[2]) : 0.0));
        }
    }

    private Integer determineChefId(Integer requestedChefId, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            if (requestedChefId == null) throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_ADMIN_REQUIRE_CHEF));
            User chef = userRepository.findById(requestedChefId).orElseThrow();
            if (chef.getRole() != Role.CHEF) throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_USER_NOT_CHEF));
            return requestedChefId;
        }
        if (currentUser.getRole() == Role.ELEVATED) {
            if (currentUser.getTeacher() == null) throw new InvalidOperationException("Usuario ELEVATED debe tener un profesor asignado.");
            return currentUser.getTeacher().getId();
        }
        return currentUser.getId();
    }

    private void checkPermissions(WeeklyPlan plan) {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) return;
        if (currentUser.getRole() == Role.ELEVATED) {
            if (currentUser.getTeacher() == null || !currentUser.getTeacher().getId().equals(plan.getChef().getId())) {
                throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NO_PERMISSION));
            }
        } else if (!plan.getChef().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_NO_PERMISSION));
        }
    }

    private List<WeeklyPlanSlot> mapSlots(List<WeeklyPlanSlotRequestDTO> dtos, WeeklyPlan plan, Integer chefId, LocalDate weekStart) {
        List<UserProjection> students = userRepository.findProjectedByTeacherIdAndIsHiddenFalse(chefId);
        Set<Integer> validIds = students.stream().map(UserProjection::getId).collect(Collectors.toSet());

        Set<Integer> recipeIds = dtos.stream().map(WeeklyPlanSlotRequestDTO::getRecipeId).collect(Collectors.toSet());
        Map<Integer, Recipe> recipeMap = recipeRepository.findAllById(recipeIds).stream().collect(Collectors.toMap(Recipe::getId, r -> r));

        Set<Integer> allStudentIds = dtos.stream().filter(d -> d.getStudentIds() != null).flatMap(d -> d.getStudentIds().stream()).collect(Collectors.toSet());
        Map<Integer, User> studentMap = userRepository.findAllById(allStudentIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<WeeklyPlanSlot> slots = new ArrayList<>();
        for (WeeklyPlanSlotRequestDTO dto : dtos) {
            Set<Integer> studentIdsInSlot = new java.util.HashSet<>();
            // Validar solapamientos
            for (WeeklyPlanSlot existing : slots) {
                if (existing.getDayOfWeek().equals(dto.getDayOfWeek())) {
                    if (dto.getStartTime().isBefore(existing.getEndTime()) && dto.getEndTime().isAfter(existing.getStartTime())) {
                        throw new InvalidOperationException("Slot overlap detected for day " + dto.getDayOfWeek());
                    }
                }
            }
            Recipe recipe = recipeMap.get(dto.getRecipeId());
            if (recipe == null) throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_RECIPE_NOT_FOUND, new Object[]{dto.getRecipeId()}));
            
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
                    if (!validIds.contains(sid)) throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_STUDENT_INVALID, new Object[]{sid}));
                    
                    String dayStudentKey = dto.getDayOfWeek() + "-" + sid;
                    if (!studentIdsInSlot.add(sid)) { 
                         log.warn("Student {} added twice in slot", sid);
                         throw new InvalidOperationException("Student " + sid + " added twice in slot");
                    }
                    
                    User studentRef = studentMap.get(sid);
                    if (studentRef == null) studentRef = userRepository.getReferenceById(sid);
                    WeeklyPlanSlotStudent wss = WeeklyPlanSlotStudent.builder()
                            .slot(slot)
                            .student(studentRef)
                            .status(StudentSlotStatus.ASSIGNED)
                            .build();
                    slot.getStudents().add(wss);
                }
            }
            slots.add(slot);
        }
        return slots;
    }
}
