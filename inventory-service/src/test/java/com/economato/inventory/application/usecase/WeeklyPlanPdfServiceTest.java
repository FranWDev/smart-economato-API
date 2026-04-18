package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.economato.inventory.application.dto.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotStudentResponseDTO;
import com.economato.inventory.infrastructure.adapter.out.external.reports.WeeklyPlanPdfService;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanPdfServiceTest {

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private WeeklyPlanPdfService weeklyPlanPdfService;

    private WeeklyPlanResponseDTO testPlan;

    @BeforeEach
    void setUp() {
        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
        lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                Object arg = invocation.getArgument(1);
                String argsStr = arg instanceof Object[] ? Arrays.toString((Object[]) arg) : String.valueOf(arg);
                return ((MessageKey) invocation.getArgument(0)).name() + " " + argsStr;
            });
            
        testPlan = new WeeklyPlanResponseDTO();
        testPlan.setId(1L);
        testPlan.setChefId(10);
        testPlan.setChefName("Test Chef");
        testPlan.setWeekStartDate(LocalDate.of(2026, 2, 9));
        testPlan.setWeekEndDate(LocalDate.of(2026, 2, 15));

        List<WeeklyPlanSlotResponseDTO> slots = new ArrayList<>();
        slots.add(createSlot(1L, "Recipe A", "2.0", 1, LocalTime.of(9,0), LocalTime.of(11,0), 1, Arrays.asList("Student 1", "Student 2")));
        slots.add(createSlot(2L, "Recipe B", "1.5", 3, LocalTime.of(11,30), LocalTime.of(13,0), 2, Arrays.asList("Student 3")));
        testPlan.setSlots(slots);
    }

    @Test
    void generateWeeklyPlanPdf_WithCompletePlan_ShouldGeneratePdf() throws Exception {
        byte[] pdfBytes = weeklyPlanPdfService.generateWeeklyPlanPdf(testPlan);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, 4));
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    void generateWeeklyPlanPdf_WithEmptySlots_ShouldGeneratePdf() throws Exception {
        testPlan.setSlots(new ArrayList<>());
        byte[] pdfBytes = weeklyPlanPdfService.generateWeeklyPlanPdf(testPlan);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, 4));
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    void generateWeeklyPlanPdf_WithNullSlots_ShouldGeneratePdf() throws Exception {
        testPlan.setSlots(null);
        byte[] pdfBytes = weeklyPlanPdfService.generateWeeklyPlanPdf(testPlan);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateWeeklyPlanPdf_WithWeekendSlots_ShouldGeneratePdf() throws Exception {
        List<WeeklyPlanSlotResponseDTO> slots = new ArrayList<>();
        // Slot on Sunday (day 7)
        slots.add(createSlot(3L, "Weekend Recipe", "3.0", 7, LocalTime.of(10,0), LocalTime.of(12,0), 1, Arrays.asList("Student 4")));
        testPlan.setSlots(slots);
        
        byte[] pdfBytes = weeklyPlanPdfService.generateWeeklyPlanPdf(testPlan);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, 4));
        assertEquals("%PDF", pdfHeader);
    }

    private WeeklyPlanSlotResponseDTO createSlot(Long id, String recipeName, String quantity, Integer dayOfWeek, LocalTime start, LocalTime end, Integer sortOrder, List<String> studentNames) {
        WeeklyPlanSlotResponseDTO slot = new WeeklyPlanSlotResponseDTO();
        slot.setId(id);
        slot.setRecipeName(recipeName);
        slot.setQuantity(new BigDecimal(quantity));
        slot.setDayOfWeek(dayOfWeek);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setSortOrder(sortOrder);
        
        if (studentNames != null) {
            List<WeeklyPlanSlotStudentResponseDTO> students = new ArrayList<>();
            for (String name : studentNames) {
                WeeklyPlanSlotStudentResponseDTO student = new WeeklyPlanSlotStudentResponseDTO();
                student.setStudentName(name);
                students.add(student);
            }
            slot.setStudents(students);
        }
        
        return slot;
    }
}
