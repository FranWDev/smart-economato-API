package com.economato.inventory.infrastructure.adapter.in.web.recipe;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.economato.inventory.application.dto.recipe.response.RecipeAuditResponseDTO;
import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.usecase.recipe.RecipeAuditService;
import com.economato.inventory.application.usecase.user.TokenBlacklistService;
import com.economato.inventory.infrastructure.config.shared.security.JwtUtils;
import com.economato.inventory.infrastructure.config.shared.security.SecurityConfig;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;

@WebMvcTest(RecipeAuditController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class RecipeAuditControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipeAuditService recipeAuditService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private I18nService i18nService;

    @MockitoBean
    private LocaleResolver localeResolver;

    @MockitoBean
    private CacheManager cacheManager;

    private RecipeAuditResponseDTO testRecipeAudit;
    private List<RecipeAuditResponseDTO> testRecipeAudits;

    @BeforeEach
    void setUp() {
        testRecipeAudit = new RecipeAuditResponseDTO(
                1,
                1,
                "MODIFICACION",
                "Receta actualizada - cambio de costos",
                LocalDateTime.now(),
                null,
                null);

        testRecipeAudits = Arrays.asList(testRecipeAudit);
    }

    @Test
    void getAllRecipeAudits_ShouldReturnPage() throws Exception {
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(recipeAuditService.findAll(any(Pageable.class))).thenReturn(new RestPage<>(testRecipeAudits, pageRequest, testRecipeAudits.size()));

        mockMvc.perform(get("/api/recipe-audits").with(user("admin").roles("ADMIN"))
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id_recipe").value(1))
                .andExpect(jsonPath("$.content[0].id_user").value(1))
                .andExpect(jsonPath("$.content[0].action").value("MODIFICACION"));
    }

    @Test
    
    void getRecipeAuditById_WhenExists_ShouldReturnAudit() throws Exception {

        when(recipeAuditService.findById(1)).thenReturn(Optional.of(testRecipeAudit));

        mockMvc.perform(get("/api/recipe-audits/1").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_recipe").value(1))
                .andExpect(jsonPath("$.id_user").value(1))
                .andExpect(jsonPath("$.action").value("MODIFICACION"));
    }

    @Test
    
    void getRecipeAuditById_WhenNotExists_ShouldReturn404() throws Exception {

        when(recipeAuditService.findById(anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/recipe-audits/999").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    
    void getAuditsByRecipeId_ShouldReturnList() throws Exception {

        when(recipeAuditService.findByRecipeId(1)).thenReturn(testRecipeAudits);

        mockMvc.perform(get("/api/recipe-audits/by-recipe/1").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id_recipe").value(1));
    }

    @Test
    
    void getAuditsByUserId_ShouldReturnList() throws Exception {

        when(recipeAuditService.findByUserId(1)).thenReturn(testRecipeAudits);

        mockMvc.perform(get("/api/recipe-audits/by-user/1").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id_user").value(1));
    }

    @Test
    
    void getAuditsByDateRange_ShouldReturnList() throws Exception {

        when(recipeAuditService.findByMovementDateBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(testRecipeAudits);

        mockMvc.perform(get("/api/recipe-audits/by-date-range").with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .param("start", "2026-01-01T00:00:00")
                .param("end", "2026-02-01T23:59:59")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id_recipe").value(1));
    }
}
