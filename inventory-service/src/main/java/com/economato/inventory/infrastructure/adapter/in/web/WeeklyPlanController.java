package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.WeeklyPlanRequestDTO;
import com.economato.inventory.application.dto.response.StudentMetricsResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.dto.response.ConfirmDayResponseDTO;
import com.economato.inventory.application.usecase.WeeklyPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.economato.inventory.application.dto.response.WeeklyPlanStockRequirementDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotStudentResponseDTO;

@RestController
@RequestMapping("/api/weekly-plans")
@RequiredArgsConstructor
public class WeeklyPlanController {

    private final WeeklyPlanService weeklyPlanService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanResponseDTO> createPlan(@Valid @RequestBody WeeklyPlanRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weeklyPlanService.createPlan(request));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanResponseDTO> updatePlan(@PathVariable Long planId,
            @Valid @RequestBody WeeklyPlanRequestDTO request) {
        return ResponseEntity.ok(weeklyPlanService.updatePlan(planId, request));
    }

    @PatchMapping("/{planId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanResponseDTO> activatePlan(@PathVariable Long planId) {
        return ResponseEntity.ok(weeklyPlanService.activatePlan(planId));
    }

    @PatchMapping("/{planId}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanResponseDTO> deactivatePlan(@PathVariable Long planId) {
        return ResponseEntity.ok(weeklyPlanService.deactivatePlan(planId));
    }

    @PatchMapping("/{planId}/slots/{slotId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanSlotResponseDTO> confirmSlot(@PathVariable Long planId, @PathVariable Long slotId) {
        return ResponseEntity.ok(weeklyPlanService.confirmSlot(planId, slotId));
    }

    @PatchMapping("/{planId}/slots/{slotId}/unconfirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WeeklyPlanSlotResponseDTO> unconfirmSlot(@PathVariable Long planId,
            @PathVariable Long slotId) {
        return ResponseEntity.ok(weeklyPlanService.unconfirmSlot(planId, slotId));
    }

    @PatchMapping("/{planId}/days/{dayOfWeek}/unconfirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfirmDayResponseDTO> unconfirmDay(
            @PathVariable Long planId,
            @PathVariable Integer dayOfWeek) {
        return ResponseEntity.ok(weeklyPlanService.unconfirmDay(planId, dayOfWeek));
    }

    @PatchMapping("/{planId}/days/{dayOfWeek}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<ConfirmDayResponseDTO> confirmDay(
            @PathVariable Long planId,
            @PathVariable Integer dayOfWeek) {
        return ResponseEntity.ok(weeklyPlanService.confirmDay(planId, dayOfWeek));
    }

    @GetMapping("/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanResponseDTO> getPlanById(@PathVariable Long planId) {
        return ResponseEntity.ok(weeklyPlanService.getPlanById(planId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<Page<WeeklyPlanResponseDTO>> getAllPlans(Pageable pageable) {
        return ResponseEntity.ok(weeklyPlanService.getAllPlans(pageable));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanResponseDTO> getCurrentWeekPlan() {
        return ResponseEntity.ok(weeklyPlanService.getCurrentWeekPlan());
    }

    @GetMapping("/{planId}/stock-requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<List<WeeklyPlanStockRequirementDTO>> getStockRequirements(@PathVariable Long planId) {
        return ResponseEntity.ok(weeklyPlanService.getStockRequirements(planId));
    }

    @PatchMapping("/{planId}/slots/{slotId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanSlotResponseDTO> cancelSlot(@PathVariable Long planId, @PathVariable Long slotId) {
        return ResponseEntity.ok(weeklyPlanService.cancelSlot(planId, slotId));
    }
    
    @PatchMapping("/{planId}/slots/{slotId}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    public ResponseEntity<WeeklyPlanSlotResponseDTO> restoreSlot(@PathVariable Long planId, @PathVariable Long slotId) {
        return ResponseEntity.ok(weeklyPlanService.restoreSlot(planId, slotId));
    }

    @PatchMapping("/{planId}/slots/{slotId}/students/{studentId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanSlotStudentResponseDTO> cancelStudentFromSlot(
            @PathVariable Long planId,
            @PathVariable Long slotId,
            @PathVariable Integer studentId) {
        return ResponseEntity.ok(weeklyPlanService.cancelStudentFromSlot(planId, slotId, studentId));
    }
    
    @PatchMapping("/{planId}/slots/{slotId}/students/{studentId}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    public ResponseEntity<WeeklyPlanSlotStudentResponseDTO> restoreStudentInSlot(
            @PathVariable Long planId, @PathVariable Long slotId, @PathVariable Integer studentId) {
        return ResponseEntity.ok(weeklyPlanService.restoreStudentInSlot(planId, slotId, studentId));
    }

    @PatchMapping("/{planId}/days/{dayOfWeek}/students/{studentId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<Void> cancelStudentFromDay(
            @PathVariable Long planId,
            @PathVariable Integer dayOfWeek,
            @PathVariable Integer studentId) {
        weeklyPlanService.cancelStudentFromDay(planId, dayOfWeek, studentId);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{planId}/days/{dayOfWeek}/students/{studentId}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    public ResponseEntity<Void> restoreStudentFromDay(
            @PathVariable Long planId,
            @PathVariable Integer dayOfWeek,
            @PathVariable Integer studentId) {
        weeklyPlanService.restoreStudentFromDay(planId, dayOfWeek, studentId);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{planId}/days/{dayOfWeek}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    public ResponseEntity<Void> restoreDay(
            @PathVariable Long planId,
            @PathVariable Integer dayOfWeek) {
        weeklyPlanService.restoreDay(planId, dayOfWeek);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<Page<StudentMetricsResponseDTO>> getStudentMetrics(
            @RequestParam(required = false) Integer chefId,
            Pageable pageable) {
        return ResponseEntity.ok(weeklyPlanService.getStudentMetrics(chefId, pageable));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAllPlans() {
        weeklyPlanService.deleteAllPlans();
        return ResponseEntity.noContent().build();
    }
}
