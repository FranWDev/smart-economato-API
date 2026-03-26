package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.LoginRequestDTO;
import com.economato.inventory.application.dto.request.WeeklyPlanRequestDTO;
import com.economato.inventory.application.dto.request.WeeklyPlanSlotRequestDTO;
import com.economato.inventory.application.dto.response.LoginResponseDTO;
import com.economato.inventory.domain.model.*;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class WeeklyPlanServiceTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private WeeklyPlanRepository weeklyPlanRepository;
    @Autowired private WeeklyPlanSlotRepository slotRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductBatchRepository productBatchRepository;

    private User chef1, chef2, student1, student2;
    private String chef1Token, chef2Token;
    private Recipe recipe;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();

        chef1 = userRepository.saveAndFlush(TestDataUtil.createChefUser());
        chef1.setUser("chef1");
        chef1 = userRepository.saveAndFlush(chef1);

        chef2 = TestDataUtil.createUser("Chef2", "chef2", "pass", Role.CHEF);
        chef2 = userRepository.saveAndFlush(chef2);

        student1 = TestDataUtil.createUser("Student1", "student1", "pass", Role.USER);
        student1.setTeacher(chef1);
        student1 = userRepository.saveAndFlush(student1);

        student2 = TestDataUtil.createUser("Student2", "student2", "pass", Role.USER);
        student2.setTeacher(chef2);
        student2 = userRepository.saveAndFlush(student2);

        Product flour = productRepository.saveAndFlush(TestDataUtil.createFlour());

        recipe = TestDataUtil.createRecipe("Test", "E", "P", BigDecimal.TEN);
        RecipeComponent rc = TestDataUtil.createRecipeComponent(recipe, flour, new BigDecimal("0.5"));
        recipe.setComponents(new java.util.HashSet<>(List.of(rc)));
        recipe = recipeRepository.saveAndFlush(recipe);

        // Crear lote para que el confirmSlot tenga stock "físico"
        ProductBatch batch = ProductBatch.builder()
                .product(flour)
                .initialQuantity(new BigDecimal("100.0"))
                .remainingQuantity(new BigDecimal("100.0"))
                .expirationDate(LocalDate.now().plusMonths(6))
                .receivedAt(LocalDateTime.now())
                .depleted(false)
                .build();
        productBatchRepository.saveAndFlush(batch);

        chef1Token = getToken("chef1", "chef123");
        chef2Token = getToken("chef2", "pass");
    }

    private String getToken(String username, String pass) throws Exception {
        LoginRequestDTO login = new LoginRequestDTO();
        login.setName(username);
        login.setPassword(pass);

        String rb = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        
        return objectMapper.readValue(rb, LoginResponseDTO.class).getToken();
    }

    @Test
    void whenCreatePlan_withExistingCancelledPlan_thenReturnsError() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        
        // 1. Create and cancel a plan
        WeeklyPlan plan = WeeklyPlan.builder()
                .chef(chef1)
                .weekStartDate(nextMonday)
                .weekEndDate(nextMonday.plusDays(6))
                .status(WeeklyPlanStatus.CANCELLED)
                .build();
        weeklyPlanRepository.saveAndFlush(plan);

        // 2. Try to create another one for same week
        WeeklyPlanSlotRequestDTO slot = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student1.getId()));
        WeeklyPlanRequestDTO request = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(slot));

        mockMvc.perform(post("/api/v1/weekly-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request))
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("existe")));
    }

    @Test
    void whenGetStudentMetrics_asChef_withoutId_thenReturnsOnlyOwnStudents() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        
        // Create plan and slot for chef2 with student2
        WeeklyPlan plan = WeeklyPlan.builder()
                .chef(chef2)
                .weekStartDate(nextMonday)
                .weekEndDate(nextMonday.plusDays(6))
                .status(WeeklyPlanStatus.ACTIVE)
                .build();
        plan = weeklyPlanRepository.saveAndFlush(plan);

        WeeklyPlanSlot slot = WeeklyPlanSlot.builder()
                .weeklyPlan(plan)
                .recipe(recipe)
                .quantity(BigDecimal.ONE)
                .dayOfWeek(1)
                .startTime(LocalTime.of(10,0))
                .endTime(LocalTime.of(11,0))
                .sortOrder(1)
                .status(WeeklyPlanSlotStatus.PENDING)
                .build();
        slot = slotRepository.saveAndFlush(slot);

        WeeklyPlanSlotStudent wss = WeeklyPlanSlotStudent.builder()
                .slot(slot)
                .student(student2)
                .status(StudentSlotStatus.ASSIGNED)
                .build();
        slot.getStudents().add(wss);
        slotRepository.saveAndFlush(slot);

        mockMvc.perform(get("/api/v1/weekly-plans/metrics/students")
                .header("Authorization", "Bearer " + chef2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].studentName", is("Student2")));
    }

    @Test
    void whenCreatePlan_withStudentAssignedToMultipleSlotsOnSameDay_thenSucceeds() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student1.getId()));
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 1, LocalTime.of(12,0), LocalTime.of(13,0), 2, List.of(student1.getId()));
        
        WeeklyPlanRequestDTO request = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(slot1, slot2));

        mockMvc.perform(post("/api/v1/weekly-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request))
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slots", hasSize(2)));
    }

    @Test
    void whenCancelSlot_alreadyCancelled_thenReturnsError() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        WeeklyPlanSlotRequestDTO s1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student1.getId()));
        WeeklyPlanRequestDTO request = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(s1));

        String rb = mockMvc.perform(post("/api/v1/weekly-plans").contentType(MediaType.APPLICATION_JSON).content(asJsonString(request)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        
        mockMvc.perform(patch("/api/v1/weekly-plans/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());
        
        String details = mockMvc.perform(get("/api/v1/weekly-plans/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(details).get("slots").get(0).get("id").asLong();

        // 1. Cancel once
        mockMvc.perform(patch("/api/v1/weekly-plans/{planId}/slots/{slotId}/cancel", planId, slotId)
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk());

        // 2. Cancel again
        mockMvc.perform(patch("/api/v1/weekly-plans/{planId}/slots/{slotId}/cancel", planId, slotId)
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ya está cancelado")));
    }

    @Test
    void whenUpdatePlan_withOverlapExistingSlot_thenReturnsError() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        
        // 1. Create a plan with TWO slots so confirming one doesn't auto-complete it
        WeeklyPlanSlotRequestDTO s1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student1.getId()));
        WeeklyPlanSlotRequestDTO s3 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 3, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student1.getId()));
        WeeklyPlanRequestDTO request = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(s1, s3));

        String rb = mockMvc.perform(post("/api/v1/weekly-plans").contentType(MediaType.APPLICATION_JSON).content(asJsonString(request)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        
        mockMvc.perform(patch("/api/v1/weekly-plans/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());
        String details = mockMvc.perform(get("/api/v1/weekly-plans/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(details).get("slots").get(0).get("id").asLong();
        mockMvc.perform(patch("/api/v1/weekly-plans/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        // Now s1 at 10:00-11:00 is CONFIRMED. s3 is unconfirmed. Plan is IN_PROGRESS.
        // Try to add s2 at 10:30-11:30 (overlaps with s1)
        WeeklyPlanSlotRequestDTO s2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 1, LocalTime.of(10,30), LocalTime.of(11,30), 2, List.of(student1.getId()));
        WeeklyPlanRequestDTO updateRequest = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(s2, s3));

        mockMvc.perform(put("/api/v1/weekly-plans/{id}", planId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateRequest))
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Solapamiento detectado con un slot existente")));
    }
    @Test
    void whenGetCurrentWeekPlan_asAdmin_thenReturnsForbidden() throws Exception {
        // Create an admin token
        User admin = userRepository.saveAndFlush(TestDataUtil.createAdminUser());
        admin.setUser("admin_test");
        admin = userRepository.saveAndFlush(admin);
        String adminToken = getToken("admin_test", "admin123");

        mockMvc.perform(get("/api/v1/weekly-plans/current")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenActivatePlan_withInProgressPlanBlockingStock_thenReturnsError() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        
        WeeklyPlanSlotRequestDTO s1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("60.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student1.getId()));
        WeeklyPlanSlotRequestDTO s2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("60.0"), 2, LocalTime.of(11,0), LocalTime.of(12,0), 2, List.of(student1.getId()));
        WeeklyPlanRequestDTO req1 = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(s1, s2));

        String rb1 = mockMvc.perform(post("/api/v1/weekly-plans").contentType(MediaType.APPLICATION_JSON).content(asJsonString(req1)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long plan1Id = objectMapper.readTree(rb1).get("id").asLong();
        
        mockMvc.perform(patch("/api/v1/weekly-plans/{id}/activate", plan1Id).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        String details1 = mockMvc.perform(get("/api/v1/weekly-plans/{id}", plan1Id).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slot1Id = objectMapper.readTree(details1).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/v1/weekly-plans/{planId}/slots/{slotId}/confirm", plan1Id, slot1Id).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        WeeklyPlanSlotRequestDTO s3 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1000.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student2.getId()));
        WeeklyPlanRequestDTO req2 = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(s3));

        String rb2 = mockMvc.perform(post("/api/v1/weekly-plans").contentType(MediaType.APPLICATION_JSON).content(asJsonString(req2)).header("Authorization", "Bearer " + chef2Token)).andReturn().getResponse().getContentAsString();
        Long plan2Id = objectMapper.readTree(rb2).get("id").asLong();

        mockMvc.perform(patch("/api/v1/weekly-plans/{id}/activate", plan2Id).header("Authorization", "Bearer " + chef2Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("stock")));
    }

    @Test
    void whenGetAllPlans_asElevatedWithoutTeacher_thenReturnsError() throws Exception {
        User elevated = TestDataUtil.createUser("Elevated", "elevated1", "pass", Role.ELEVATED);
        elevated.setTeacher(null);
        userRepository.saveAndFlush(elevated);
        String token = getToken("elevated1", "pass");

        mockMvc.perform(get("/api/v1/weekly-plans")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("profesor")));
    }

    @Test
    void whenGetCurrentWeekPlan_asElevatedWithoutTeacher_thenReturnsError() throws Exception {
        User elevated = TestDataUtil.createUser("Elevated", "elevated2", "pass", Role.ELEVATED);
        elevated.setTeacher(null);
        userRepository.saveAndFlush(elevated);
        String token = getToken("elevated2", "pass");

        mockMvc.perform(get("/api/v1/weekly-plans/current")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("profesor")));
    }

    @Test
    void whenUpdatePlan_asInProgress_withInsufficientStock_thenReturnsError() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        
        // 1. Create and activate plan with 2 slots
        WeeklyPlanSlotRequestDTO s1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student1.getId()));
        WeeklyPlanSlotRequestDTO s3 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), BigDecimal.ONE, 3, LocalTime.of(10,0), LocalTime.of(11,0), 1, List.of(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(s1, s3));

        String rb = mockMvc.perform(post("/api/v1/weekly-plans").contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        
        mockMvc.perform(patch("/api/v1/weekly-plans/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        // 2. Confirm one slot to move plan to IN_PROGRESS (leaving 1 unconfirmed)
        String details = mockMvc.perform(get("/api/v1/weekly-plans/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(details).get("slots").get(0).get("id").asLong();
        mockMvc.perform(patch("/api/v1/weekly-plans/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        // 3. Update plan with huge quantity that exceeds stock
        WeeklyPlanSlotRequestDTO s2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1000.0"), 2, LocalTime.of(12,0), LocalTime.of(13,0), 2, List.of(student1.getId()));
        WeeklyPlanRequestDTO updateReq = TestDataUtil.createWeeklyPlanRequestDTO(null, nextMonday, List.of(s2, s3)); // send existing unconfirmed as well

        mockMvc.perform(put("/api/v1/weekly-plans/{id}", planId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateReq))
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("stock")));
    }
}
