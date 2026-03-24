package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.WeeklyPlanRequestDTO;
import com.economato.inventory.application.dto.response.StudentMetricsResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.usecase.WeeklyPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/weekly-plans")
@RequiredArgsConstructor
public class WeeklyPlanController {

    private final WeeklyPlanService weeklyPlanService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    public ResponseEntity<WeeklyPlanResponseDTO> createPlan(@Valid @RequestBody WeeklyPlanRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weeklyPlanService.createPlan(request));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanResponseDTO> updatePlan(@PathVariable Long planId, @Valid @RequestBody WeeklyPlanRequestDTO request) {
        return ResponseEntity.ok(weeklyPlanService.updatePlan(planId, request));
    }

    @PatchMapping("/{planId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    public ResponseEntity<WeeklyPlanResponseDTO> activatePlan(@PathVariable Long planId) {
        return ResponseEntity.ok(weeklyPlanService.activatePlan(planId));
    }

    @PatchMapping("/{planId}/slots/{slotId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    public ResponseEntity<WeeklyPlanSlotResponseDTO> confirmSlot(@PathVariable Long planId, @PathVariable Long slotId) {
        return ResponseEntity.ok(weeklyPlanService.confirmSlot(planId, slotId));
    }

    @GetMapping("/metrics/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")
    public ResponseEntity<Page<StudentMetricsResponseDTO>> getStudentMetrics(
            @RequestParam(required = false) Integer chefId,
            Pageable pageable) {
        return ResponseEntity.ok(weeklyPlanService.getStudentMetrics(chefId, pageable));
    }
}
