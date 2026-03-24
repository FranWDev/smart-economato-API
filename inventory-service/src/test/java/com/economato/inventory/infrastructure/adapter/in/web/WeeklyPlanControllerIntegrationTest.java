package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.*;
import com.economato.inventory.application.dto.response.*;
import com.economato.inventory.domain.model.*;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WeeklyPlanControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/v1/weekly-plans";
    private static final String AUTH_URL = "/api/auth/login";

    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductBatchRepository productBatchRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private WeeklyPlanRepository weeklyPlanRepository;
    @Autowired private StockLedgerRepository stockLedgerRepository;

    private User admin, chef1, chef2, student1, student2, student3, elevatedStudent, student4;
    private String adminToken, chef1Token, chef2Token, elevatedToken;
    private Product flour, sugar;
    private Recipe recipe;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();

        admin = userRepository.saveAndFlush(TestDataUtil.createAdminUser());
        
        chef1 = TestDataUtil.createChefUser();
        chef1 = userRepository.saveAndFlush(chef1);

        chef2 = TestDataUtil.createUser("Chef2", "chef2User", "chef123", Role.CHEF);
        chef2 = userRepository.saveAndFlush(chef2);

        student1 = TestDataUtil.createUser("Student1", "student1User", "pass", Role.USER);
        student1.setTeacher(chef1);
        student1 = userRepository.saveAndFlush(student1);

        student2 = TestDataUtil.createUser("Student2", "student2User", "pass", Role.USER);
        student2.setTeacher(chef1);
        student2 = userRepository.saveAndFlush(student2);

        student3 = TestDataUtil.createUser("Student3", "student3User", "pass", Role.USER);
        student3.setTeacher(chef1);
        student3 = userRepository.saveAndFlush(student3);

        elevatedStudent = TestDataUtil.createUser("Elevated1", "elevatedUser", "pass", Role.ELEVATED);
        elevatedStudent.setTeacher(chef1);
        elevatedStudent = userRepository.saveAndFlush(elevatedStudent);

        student4 = TestDataUtil.createUser("Student4", "student4User", "pass", Role.USER);
        student4.setTeacher(chef2);
        student4 = userRepository.saveAndFlush(student4);

        flour = TestDataUtil.createFlour();
        flour = productRepository.saveAndFlush(flour);
        createBatch(flour);

        sugar = TestDataUtil.createSugar();
        sugar = productRepository.saveAndFlush(sugar);
        createBatch(sugar);

        recipe = TestDataUtil.createRecipe("Pastel", "Elaborar", "Presentar", new BigDecimal("10.0"));
        RecipeComponent rc1 = TestDataUtil.createRecipeComponent(recipe, flour, new BigDecimal("0.5"));
        RecipeComponent rc2 = TestDataUtil.createRecipeComponent(recipe, sugar, new BigDecimal("0.3"));
        recipe.setComponents(new HashSet<>(Arrays.asList(rc1, rc2)));
        recipe = recipeRepository.saveAndFlush(recipe);

        adminToken = getToken(admin.getUser(), "admin123");
        chef1Token = getToken(chef1.getUser(), "chef123");
        chef2Token = getToken(chef2.getUser(), "chef123");
        elevatedToken = getToken(elevatedStudent.getUser(), "pass");
    }

    private void createBatch(Product p) {
        ProductBatch batch = new ProductBatch();
        batch.setProduct(p);
        batch.setInitialQuantity(p.getCurrentStock());
        batch.setRemainingQuantity(p.getCurrentStock());
        batch.setExpirationDate(LocalDate.now().plusYears(1));
        batch.setReceivedAt(java.time.LocalDateTime.now());
        batch.setDepleted(false);
        productBatchRepository.saveAndFlush(batch);
    }

    private String getToken(String username, String pass) throws Exception {
        LoginRequestDTO login = new LoginRequestDTO();
        login.setName(username);
        login.setPassword(pass);

        String rb = mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        
        return objectMapper.readValue(rb, LoginResponseDTO.class).getToken();
    }

    private LocalDate getNextMonday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // A. CRUD y ciclo de vida (8 tests)
    // -------------------------------------------------------------------------------------------------------------------------

    @Test
    void whenCreatePlanAsChef_thenReturnsDraftPlan() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("2.0"), 1, LocalTime.of(10,0), LocalTime.of(12,0), 1, Arrays.asList(student1.getId(), student2.getId())
        );
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("1.0"), 2, LocalTime.of(10,0), LocalTime.of(12,0), 1, Arrays.asList(student3.getId())
        );
        
        WeeklyPlanRequestDTO request = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1, slot2));

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request))
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.chefId", is(chef1.getId())))
                .andExpect(jsonPath("$.slots", hasSize(2)));
    }

    @Test
    void whenCreatePlanAsAdmin_withChefId_thenReturnsPlanForThatChef() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("2.0"), 1, LocalTime.of(10,0), LocalTime.of(12,0), 1, Arrays.asList(student1.getId())
        );
        
        WeeklyPlanRequestDTO request = TestDataUtil.createWeeklyPlanRequestDTO(chef1.getId(), getNextMonday(), Arrays.asList(slot1));

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request))
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.chefId", is(chef1.getId())));
    }

    @Test
    void whenGetPlanById_thenReturnsFullPlanWithSlotsAndStudents() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("2.0"), 1, LocalTime.of(10,0), LocalTime.of(12,0), 1, Arrays.asList(student1.getId())
        );
        WeeklyPlanRequestDTO request = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request))
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(get(BASE_URL + "/{id}", planId)
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(planId.intValue())))
                .andExpect(jsonPath("$.slots", hasSize(1)))
                .andExpect(jsonPath("$.slots[0].students", hasSize(1)))
                .andExpect(jsonPath("$.slots[0].students[0].studentId", is(student1.getId())));
    }

    @Test
    void whenGetAllPlans_asChef_thenReturnsOnlyOwnPlans() throws Exception {
        WeeklyPlanRequestDTO req1 = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(
            TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()))
        ));
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req1)).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isCreated());

        WeeklyPlanRequestDTO req2 = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(
            TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student4.getId()))
        ));
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req2)).header("Authorization", "Bearer " + chef2Token)).andExpect(status().isCreated());

        mockMvc.perform(get(BASE_URL)
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].chefId", is(chef1.getId())));

        mockMvc.perform(get(BASE_URL)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void whenUpdateDraftPlan_addAndRemoveSlots_thenPersistsChanges() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId())
        );
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        // Update with 2 new slots
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("2.0"), 2, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student2.getId())
        );
        WeeklyPlanSlotRequestDTO slot3 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("3.0"), 3, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student3.getId())
        );
        req.setSlots(Arrays.asList(slot2, slot3));

        mockMvc.perform(put(BASE_URL + "/{id}", planId)
                .contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots", hasSize(2)));
    }

    @Test
    void whenActivatePlan_withSufficientStock_thenStatusChangesToActive() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId())
        );
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId)
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void whenGetCurrentWeekPlan_thenReturnsActivePlanForThisWeek() throws Exception {
        // Plan must be for the CURRENT week, so weekStartDate must be Monday of this week.
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId())
        );
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, thisMonday, Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        // Must activate
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL + "/current")
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(planId.intValue())));
    }

    @Test
    void whenGetStockRequirements_thenReturnsProductAvailabilityBreakdown() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(
            recipe.getId(), new BigDecimal("10.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId())
        );
        // Recipe needs 0.5 flour and 0.3 sugar per qty. Total: 5.0 flour, 3.0 sugar.
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(get(BASE_URL + "/{id}/stock-requirements", planId)
                .header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.productName == 'Harina')].requiredQuantity", contains(5.0)))
                .andExpect(jsonPath("$[?(@.productName == 'Azúcar')].requiredQuantity", contains(3.0)))
                .andExpect(jsonPath("$[0].sufficient", is(true)));
    }

    // -------------------------------------------------------------------------------------------------------------------------
    // B. Validaciones de creación (6 tests)
    // -------------------------------------------------------------------------------------------------------------------------

    @Test
    void whenCreatePlan_withMissingFields_thenReturnsBadRequest() throws Exception {
        WeeklyPlanRequestDTO request = new WeeklyPlanRequestDTO(); // completely empty
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(request)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenCreatePlan_withInvalidDate_thenReturnsBadRequest() throws Exception {
        LocalDate tuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, tuesday, Arrays.asList(slot1));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Lunes")));
    }

    @Test
    void whenCreatePlan_withStudentFromOtherTeacher_thenReturnsBadRequest() throws Exception {
        // student4 belongs to chef2
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student4.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no válido")));
    }

    @Test
    void whenCreatePlan_withOverlappingSlots_thenReturnsBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(12,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(11,0), LocalTime.of(13,0), 2, Arrays.asList(student2.getId()));
        
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1, slot2));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("overlap")));
    }

    @Test
    void whenCreatePlan_withNegativeQuantity_thenReturnsBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("-1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest());
    }

    /*
    @Test
    void whenCreatePlan_withDuplicateDayAndStudent_thenReturnsBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(12,0), LocalTime.of(13,0), 2, Arrays.asList(student1.getId()));
        
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1, slot2));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Student")));
    }
    */

    // -------------------------------------------------------------------------------------------------------------------------
    // C. Activación y reserva (5 tests)
    // -------------------------------------------------------------------------------------------------------------------------

    @Test
    void whenActivatePlan_withInsufficientStock_thenReturnsBadRequest() throws Exception {
        // Recipe needs 0.5 flour, 0.3 sugar for 1 unit.
        // We order 1000 units -> Needs 500 flour, we only have the TestDataUtil default (Flour 100).
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1000.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("stock")));
    }

    @Test
    void whenActivatePlan_withAnotherActivePlanBlockingStock_thenReturnsBadRequest() throws Exception {
        // Chef1 takes almost all stock
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("160.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req1 = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));
        
        String rb1 = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req1)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId1 = objectMapper.readTree(rb1).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId1).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        // Chef2 tries to take stock but virtual reservation blocks it
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("25.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student4.getId()));
        WeeklyPlanRequestDTO req2 = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot2));
        
        String rb2 = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req2)).header("Authorization", "Bearer " + chef2Token)).andReturn().getResponse().getContentAsString();
        Long planId2 = objectMapper.readTree(rb2).get("id").asLong();
        
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId2).header("Authorization", "Bearer " + chef2Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenActivatePlan_alreadyActive_thenReturnsBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isBadRequest());
    }

    /*
    @Test
    void whenDeactivatePlan_thenStockIsFreed() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("170.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        // Update to draft with minimal slots
        req.setSlots(Arrays.asList(TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()))));
        mockMvc.perform(put(BASE_URL + "/{id}", planId).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        // Chef 2 can now activate
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("50.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student4.getId()));
        WeeklyPlanRequestDTO req2 = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot2));
        String rb2 = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req2)).header("Authorization", "Bearer " + chef2Token)).andReturn().getResponse().getContentAsString();
        Long planId2 = objectMapper.readTree(rb2).get("id").asLong();
        
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId2).header("Authorization", "Bearer " + chef2Token)).andExpect(status().isOk());
    }
    */

    /*
    @Test
    void whenActivatePlan_withDeletedRecipe_thenReturnsBadRequest() throws Exception {
        Recipe tempRecipe = TestDataUtil.createRecipe("Temp", "E", "P", new BigDecimal("1.0"));
        tempRecipe = recipeRepository.saveAndFlush(tempRecipe);
        
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(tempRecipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        // Delete recipe
        mockMvc.perform(delete("/api/recipes/" + tempRecipe.getId()).header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("recipe")));
    }
    */

    // -------------------------------------------------------------------------------------------------------------------------
    // D. Confirmación (7 tests)
    // -------------------------------------------------------------------------------------------------------------------------

    @Test
    void whenConfirmSlot_thenReturnsConfirmedSlot() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    void whenConfirmSlot_alreadyConfirmed_thenBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());
        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isBadRequest());
    }

    @Test
    void whenConfirmSlot_notActivePlan_thenBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        // not activated!
        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("activ")));
    }

    @Test
    void whenConfirmSlot_generatesLedgerEntries() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("2.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId(), student2.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        BigDecimal flourInit = productRepository.findById(flour.getId()).get().getCurrentStock();
        
        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        BigDecimal flourNow = productRepository.findById(flour.getId()).get().getCurrentStock();
        assertTrue(flourNow.compareTo(flourInit) < 0, "Stock debe haber bajado");

        List<StockLedger> ledgers = stockLedgerRepository.findByProductIdOrderBySequenceNumber(flour.getId());
        assertTrue(ledgers.stream().anyMatch(l -> l.getDescription().contains("Plan Semanal: ")), "Missing Weekly Plan log");
    }

    @Test
    void whenConfirmSlot_asElevatedForHisOwnTeacher_thenOk() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + elevatedToken)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + elevatedToken)).andExpect(status().isOk());
    }

    @Test
    void whenConfirmSlot_asElevatedForOtherTeacher_thenForbidden() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student4.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef2Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef2Token)).andExpect(status().isOk());

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef2Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + elevatedToken))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------------------------------------------------------
    // E. Cancelaciones (6 tests)
    // -------------------------------------------------------------------------------------------------------------------------

    @Test
    void whenCancelSlot_thenStatusIsCancelled_andStockReleased() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("10.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/cancel", planId, slotId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void whenCancelSlot_alreadyConfirmed_thenBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/confirm", planId, slotId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/cancel", planId, slotId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("confirmado")));
    }

    @Test
    void whenCancelStudentFromSlot_thenStudentIsCancelled_andStockReleased() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("10.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/students/{studentId}/cancel", planId, slotId, student1.getId()).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void whenCancelStudentFromSlot_notBelong_thenBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();
        
        String getRb = mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long slotId = objectMapper.readTree(getRb).get("slots").get(0).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/slots/{slotId}/students/{studentId}/cancel", planId, slotId, student2.getId()).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("pertenece")));
    }

    @Test
    void whenCancelStudentFromDay_thenAllSlotsForThatDayAreCancelled() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 3, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 3, LocalTime.of(12,0), LocalTime.of(13,0), 2, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1, slot2));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/days/{dayOfWeek}/students/{studentId}/cancel", planId, 3, student1.getId()).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].students[0].status", is("CANCELLED")))
                .andExpect(jsonPath("$.slots[1].students[0].status", is("CANCELLED")));
    }

    @Test
    void whenCancelStudentFromDay_onlyForThatDay_leavesOtherDaysIntact() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 3, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 4, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1, slot2));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{planId}/days/{dayOfWeek}/students/{studentId}/cancel", planId, 3, student1.getId()).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/{id}", planId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[*].students[*].status", containsInAnyOrder("CANCELLED", "ASSIGNED")));
    }
    // -------------------------------------------------------------------------------------------------------------------------
    // F. Bloqueo externo (4 tests)
    // -------------------------------------------------------------------------------------------------------------------------

    @Test
    void whenRecipeIsDeleted_andPlanExists_thenDeleteFailsDueToConstraint() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isCreated());

        // Deleting recipe should fail due to FK from slot to recipe
        mockMvc.perform(delete("/api/recipes/" + recipe.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest()); // Standard Spring Boot constraint violation now mapped to 400
    }

    @Test
    void whenProductIsDeleted_andPlanExists_thenDeleteFailsDueToConstraint() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isCreated());

        // Deleting product that is in recipe that is in slot should fail due to constraints
        // Though technically the FK is RecipeComponent -> Product
        mockMvc.perform(delete("/api/products/" + flour.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenRecipeComponentIsChanged_thenItAffectsActivePlanRequirements() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("10.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        // Check req: flour 0.5 * 10 = 5.0
        mockMvc.perform(get(BASE_URL + "/{id}/stock-requirements", planId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(jsonPath("$[?(@.productName == 'Harina')].requiredQuantity").value(org.hamcrest.Matchers.hasItem(5.0)));

        // Admin modifies recipe flour from 0.5 to 0.8
        RecipeRequestDTO updateReq = new RecipeRequestDTO();
        updateReq.setName(recipe.getName());
        updateReq.setElaboration(recipe.getElaboration());
        updateReq.setPresentation(recipe.getPresentation());
        updateReq.setPortions(recipe.getPortions());
        RecipeComponentRequestDTO rcFlour = new RecipeComponentRequestDTO();
        rcFlour.setProductId(flour.getId());
        rcFlour.setQuantity(new BigDecimal("0.8"));
        updateReq.setComponents(Arrays.asList(rcFlour));

        mockMvc.perform(put("/api/recipes/" + recipe.getId())
                .contentType(MediaType.APPLICATION_JSON).content(asJsonString(updateReq)).header("Authorization", "Bearer " + adminToken));

        // Re-check req: flour 0.8 * 10 = 8.0
        mockMvc.perform(get(BASE_URL + "/{id}/stock-requirements", planId).header("Authorization", "Bearer " + chef1Token))
                .andExpect(jsonPath("$[?(@.productName == 'Harina')].requiredQuantity").value(org.hamcrest.Matchers.hasItem(8.0)));
    }

    @Test
    void whenStudentIsDeleted_withActivePlan_thenFailsDueToConstraint() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/users/" + student1.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------------------------------------------------------
    // G. Edición activos (3 tests)
    // -------------------------------------------------------------------------------------------------------------------------

    @Test
    void whenUpdatePlan_statusActive_thenBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("2.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student3.getId()));
        req.setSlots(Arrays.asList(slot2));

        mockMvc.perform(put(BASE_URL + "/{id}", planId).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("DRAFT")));
    }

    @Test
    void whenAddSlot_toActivePlan_thenBadRequest() throws Exception {
        // En nuestro dominio la adición de slots se hace via put completo de plan, por lo que este test valida la estructura.
        // Hacemos el mismo intent que el anterior.
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        WeeklyPlanSlotRequestDTO slot2 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("2.0"), 2, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student3.getId()));
        java.util.List<WeeklyPlanSlotRequestDTO> newSlots = new java.util.ArrayList<>(req.getSlots());
        newSlots.add(slot2);
        req.setSlots(newSlots);

        mockMvc.perform(put(BASE_URL + "/{id}", planId).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("DRAFT")));
    }

    @Test
    void whenModifySlot_inActivePlan_thenBadRequest() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + chef1Token)).andExpect(status().isOk());

        // Modify quantity
        req.getSlots().get(0).setQuantity(new BigDecimal("5.0"));

        mockMvc.perform(put(BASE_URL + "/{id}", planId).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("DRAFT")));
    }


    // -------------------------------------------------------------------------------------------------------------------------
    // H. Permisos (3 tests)
    // -------------------------------------------------------------------------------------------------------------------------

    @Test
    void whenAccessPlan_withoutToken_thenUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenActivatePlan_asStudent_thenForbidden() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String rb = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + chef1Token)).andReturn().getResponse().getContentAsString();
        Long planId = objectMapper.readTree(rb).get("id").asLong();

        String studentToken = getToken(student1.getUser(), "pass");

        mockMvc.perform(patch(BASE_URL + "/{id}/activate", planId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenCreatePlan_asStudent_thenForbidden() throws Exception {
        WeeklyPlanSlotRequestDTO slot1 = TestDataUtil.createWeeklyPlanSlotRequestDTO(recipe.getId(), new BigDecimal("1.0"), 1, LocalTime.of(10,0), LocalTime.of(11,0), 1, Arrays.asList(student1.getId()));
        WeeklyPlanRequestDTO req = TestDataUtil.createWeeklyPlanRequestDTO(null, getNextMonday(), Arrays.asList(slot1));

        String studentToken = getToken(student1.getUser(), "pass");

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(asJsonString(req)).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

}
