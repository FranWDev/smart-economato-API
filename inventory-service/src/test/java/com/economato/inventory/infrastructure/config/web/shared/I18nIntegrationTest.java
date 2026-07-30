package com.economato.inventory.infrastructure.config.web.shared;

import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;

import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class I18nIntegrationTest extends BaseIntegrationTest {

    private static final String PRODUCTS_URL = "/api/products";

    @Autowired
    @Qualifier("tokenLocaleCache")
    private Cache<String, Locale> tokenLocaleCache;

    @BeforeEach
    void setUp() {
        clearDatabase();
        tokenLocaleCache.invalidateAll();
        User admin = TestDataUtil.createAdminUser();
        userRepository.saveAndFlush(admin);
    }

    @Test
    public void whenAcceptLanguageIsSpanish_thenReturnsSpanishMessages() throws Exception {
        tokenLocaleCache.invalidateAll();
        String token = loginAsAdmin();
        mockMvc.perform(post(PRODUCTS_URL)
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "es")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("El nombre del producto es obligatorio"));
    }

    @Test
    public void whenAcceptLanguageIsEnglish_thenReturnsEnglishMessages() throws Exception {
        tokenLocaleCache.invalidateAll();
        String token = loginAsAdmin();
        mockMvc.perform(post(PRODUCTS_URL)
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Product name is required"));
    }

    @Test
    public void whenAcceptLanguageIsFrench_thenReturnsFrenchMessages() throws Exception {
        tokenLocaleCache.invalidateAll();
        String token = loginAsAdmin();
        mockMvc.perform(post(PRODUCTS_URL)
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "fr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Le nom du produit est obligatoire"));
    }

    @Test
    public void whenAcceptLanguageIsCatalan_thenReturnsCatalanMessages() throws Exception {
        tokenLocaleCache.invalidateAll();
        String token = loginAsAdmin();
        mockMvc.perform(post(PRODUCTS_URL)
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "ca")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("El nom del producte és obligatori"));
    }

    @Test
    public void whenAcceptLanguageIsMissing_thenFallsBackToSpanish() throws Exception {
        tokenLocaleCache.invalidateAll();
        String token = loginAsAdmin();
        mockMvc.perform(post(PRODUCTS_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("El nombre del producto es obligatorio"));
    }

    @Test
    public void whenAcceptLanguageIsGalician_thenReturnsGalicianMessages() throws Exception {
        tokenLocaleCache.invalidateAll();
        String token = loginAsAdmin();
        mockMvc.perform(post(PRODUCTS_URL)
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "gl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("O nome do produto é obrigatorio"));
    }
}
