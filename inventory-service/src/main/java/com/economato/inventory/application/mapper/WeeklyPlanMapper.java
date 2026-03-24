package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotStudentResponseDTO;
import com.economato.inventory.domain.model.WeeklyPlan;
import com.economato.inventory.domain.model.WeeklyPlanSlot;
import com.economato.inventory.domain.model.WeeklyPlanSlotStudent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class WeeklyPlanMapper {

    public WeeklyPlanResponseDTO toResponseDTO(WeeklyPlan plan) {
        if (plan == null) return null;

        List<WeeklyPlanSlotResponseDTO> slotDTOs = plan.getSlots() != null
                ? plan.getSlots().stream().map(this::toSlotResponseDTO).collect(Collectors.toList())
                : null;

        return WeeklyPlanResponseDTO.builder()
                .id(plan.getId())
                .chefId(plan.getChef() != null ? plan.getChef().getId() : null)
                .chefName(plan.getChef() != null ? plan.getChef().getName() : null)
                .weekStartDate(plan.getWeekStartDate())
                .weekEndDate(plan.getWeekEndDate())
                .status(plan.getStatus())
                .slots(slotDTOs)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    public WeeklyPlanSlotResponseDTO toSlotResponseDTO(WeeklyPlanSlot slot) {
        if (slot == null) return null;

        List<WeeklyPlanSlotStudentResponseDTO> studentDTOs = slot.getStudents() != null
                ? slot.getStudents().stream().map(this::toStudentResponseDTO).collect(Collectors.toList())
                : null;

        return WeeklyPlanSlotResponseDTO.builder()
                .id(slot.getId())
                .recipeId(slot.getRecipe() != null ? slot.getRecipe().getId() : null)
                .recipeName(slot.getRecipe() != null ? slot.getRecipe().getName() : null)
                .quantity(slot.getQuantity())
                .dayOfWeek(slot.getDayOfWeek())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .sortOrder(slot.getSortOrder())
                .status(slot.getStatus())
                .confirmedAt(slot.getConfirmedAt())
                .confirmedByName(slot.getConfirmedBy() != null ? slot.getConfirmedBy().getName() : null)
                .students(studentDTOs)
                .build();
    }

    public WeeklyPlanSlotStudentResponseDTO toStudentResponseDTO(WeeklyPlanSlotStudent slotStudent) {
        if (slotStudent == null) return null;

        return WeeklyPlanSlotStudentResponseDTO.builder()
                .id(slotStudent.getId())
                .studentId(slotStudent.getStudent() != null ? slotStudent.getStudent().getId() : null)
                .studentName(slotStudent.getStudent() != null ? slotStudent.getStudent().getName() : null)
                .status(slotStudent.getStatus())
                .cancelledAt(slotStudent.getCancelledAt())
                .cancelledByName(slotStudent.getCancelledBy() != null ? slotStudent.getCancelledBy().getName() : null)
                .build();
    }
}
