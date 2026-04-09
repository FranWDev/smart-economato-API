package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.AttachAuditRequestDTO;
import com.economato.inventory.application.dto.request.CloseIncidentRequestDTO;
import com.economato.inventory.application.dto.request.CreateIncidentRequestDTO;
import com.economato.inventory.application.dto.request.IncidentTypeRequestDTO;
import com.economato.inventory.application.dto.request.OpenIncidentRequestDTO;
import com.economato.inventory.domain.model.Incident;
import com.economato.inventory.domain.model.IncidentStatus;
import com.economato.inventory.domain.model.IncidentType;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeCookingAudit;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.IncidentTypeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IncidentTypeRepository incidentTypeRepository;
    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeCookingAuditRepository recipeCookingAuditRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuditEventProducer auditEventProducer;

    private String adminToken;
    private User chefOne;
    private User chefTwo;
    private User elevatedWithChefTwo;
    private User regularUser;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();
        TestDataUtil.setPasswordEncoder(passwordEncoder);

        userRepository.saveAndFlush(TestDataUtil.createAdminUser());

        chefOne = saveUser("Chef One", "chefOne", "chef123", Role.CHEF, null);
        chefTwo = saveUser("Chef Two", "chefTwo", "chef123", Role.CHEF, null);
        elevatedWithChefTwo = saveUser("Elevated Two", "elevTwo", "elev123", Role.ELEVATED, chefTwo);
        regularUser = saveUser("Regular", "regularUser", "user123", Role.USER, null);

        adminToken = loginAsAdmin();
    }

    @Test
    void createIncidentType_AsAdmin_ShouldReturn201() throws Exception {
        IncidentTypeRequestDTO request = IncidentTypeRequestDTO.builder()
                .name("Sanitary")
                .description("Sanitary incidents")
                .build();

        mockMvc.perform(post("/api/incident-types")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Sanitary")));
    }

    @Test
    void createIncidentType_AsChef_ShouldReturn403() throws Exception {
        String chefToken = login("Chef One", "chef123");
        IncidentTypeRequestDTO request = IncidentTypeRequestDTO.builder()
                .name("Unauthorized")
                .description("Should fail")
                .build();

        mockMvc.perform(post("/api/incident-types")
                        .header("Authorization", bearer(chefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullIncidentLifecycle_ShouldWorkEndToEnd() throws Exception {
        String chefToken = login("Chef One", "chef123");

        IncidentType type = incidentTypeRepository.saveAndFlush(IncidentType.builder()
                .name("Cook Error")
                .description("Cook mistakes")
                .isActive(true)
                .build());

        Recipe recipe = recipeRepository.saveAndFlush(TestDataUtil.createRecipe(
                "Rice",
                "Cook",
                "Serve",
                new BigDecimal("2.00")
        ));
        RecipeCookingAudit audit = recipeCookingAuditRepository.saveAndFlush(RecipeCookingAudit.builder()
                .recipe(recipe)
                .user(chefOne)
                .quantityCooked(new BigDecimal("3.0"))
                .details("Test")
                .cookingDate(LocalDateTime.now())
                .build());

        CreateIncidentRequestDTO createRequest = CreateIncidentRequestDTO.builder()
                .incidentTypeId(type.getId())
                .title("Wrong cooked rice")
                .description("Undercooked batch")
                .cookingAuditIds(List.of(audit.getId()))
                .build();

        String createResponse = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", bearer(chefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CREADO")))
                .andReturn().getResponse().getContentAsString();

        Long incidentId = readLong(createResponse, "id");

        mockMvc.perform(get("/api/incidents")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(String.valueOf(incidentId))));

        mockMvc.perform(patch("/api/incidents/{id}/open", incidentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(OpenIncidentRequestDTO.builder().severity(com.economato.inventory.domain.model.IncidentSeverity.ALTA).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ABIERTO")));

        mockMvc.perform(multipart("/api/incidents/{id}/chat", incidentId)
                        .param("content", "Investigating")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content", is("Investigating")));

        mockMvc.perform(patch("/api/incidents/{id}/close", incidentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(CloseIncidentRequestDTO.builder().hasResolution(true).resolution("Resolved").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CERRADO_CON_RESOLUCION")));

        mockMvc.perform(get("/api/incidents/{id}/export/pdf", incidentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/pdf")));
    }

    @Test
    void createIncident_AsUser_ShouldReturn403() throws Exception {
        String userToken = login("Regular", "user123");
        IncidentType type = incidentTypeRepository.saveAndFlush(IncidentType.builder()
                .name("Type")
                .description("d")
                .isActive(true)
                .build());

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(CreateIncidentRequestDTO.builder()
                                .incidentTypeId(type.getId())
                                .title("Incident")
                                .description("Desc")
                                .build())))
                .andExpect(status().isForbidden());
    }

    @Test
    void openIncident_AsChef_ShouldReturn403() throws Exception {
        String chefToken = login("Chef One", "chef123");
        Incident incident = persistIncident(chefOne, null, IncidentStatus.CREADO, true);

        mockMvc.perform(patch("/api/incidents/{id}/open", incident.getId())
                        .header("Authorization", bearer(chefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(OpenIncidentRequestDTO.builder()
                                .severity(com.economato.inventory.domain.model.IncidentSeverity.MEDIA)
                                .build())))
                .andExpect(status().isForbidden());
    }

    @Test
    void closeIncident_AsChef_ShouldReturn403() throws Exception {
        String chefToken = login("Chef One", "chef123");
        Incident incident = persistIncident(chefOne, null, IncidentStatus.ABIERTO, true);

        mockMvc.perform(patch("/api/incidents/{id}/close", incident.getId())
                        .header("Authorization", bearer(chefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(CloseIncidentRequestDTO.builder().hasResolution(false).build())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getIncident_AsNonParticipant_ShouldReturn403() throws Exception {
        String chefOneToken = login("Chef One", "chef123");
        Incident incident = persistIncident(elevatedWithChefTwo, chefTwo, IncidentStatus.CREADO, true);

        mockMvc.perform(get("/api/incidents/{id}", incident.getId())
                        .header("Authorization", bearer(chefOneToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createIncident_WithInactiveType_ShouldReturn400() throws Exception {
        String chefToken = login("Chef One", "chef123");
        IncidentType inactive = incidentTypeRepository.saveAndFlush(IncidentType.builder()
                .name("Inactive type")
                .description("d")
                .isActive(false)
                .build());

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", bearer(chefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(CreateIncidentRequestDTO.builder()
                                .incidentTypeId(inactive.getId())
                                .title("Incident")
                                .description("Desc")
                                .build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeIncident_WithResolutionFlagButNoText_ShouldReturn400() throws Exception {
        Incident incident = persistIncident(chefOne, null, IncidentStatus.ABIERTO, true);

        mockMvc.perform(patch("/api/incidents/{id}/close", incident.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(CloseIncidentRequestDTO.builder().hasResolution(true).resolution("   ").build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void attachAudit_ToClosedIncident_ShouldReturn400() throws Exception {
        String chefToken = login("Chef One", "chef123");
        Incident incident = persistIncident(chefOne, null, IncidentStatus.CERRADO_CON_RESOLUCION, true);

        mockMvc.perform(post("/api/incidents/{id}/audits", incident.getId())
                        .header("Authorization", bearer(chefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(AttachAuditRequestDTO.builder().cookingAuditIds(List.of(123L)).build())))
                .andExpect(status().isBadRequest());
    }

    private User saveUser(String name, String username, String password, Role role, User teacher) {
        User user = TestDataUtil.createUser(name, username, password, role);
        user.setTeacher(teacher);
        return userRepository.saveAndFlush(user);
    }

    private Incident persistIncident(User creator, User relatedTeacher, com.economato.inventory.domain.model.IncidentStatus status, boolean activeType) {
        IncidentType type = incidentTypeRepository.saveAndFlush(IncidentType.builder()
                .name("Type " + Math.random())
                .description("d")
                .isActive(activeType)
                .build());
        return incidentRepository.saveAndFlush(Incident.builder()
                .incidentType(type)
                .title("Incident")
                .description("Desc")
                .status(status)
                .createdBy(creator)
                .relatedTeacher(relatedTeacher)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long readLong(String json, String key) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get(key).asLong();
    }
}
