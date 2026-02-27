package com.economato.inventory.i18n;

import com.economato.inventory.controller.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class I18nIntegrationTest extends BaseIntegrationTest {

    private static final String LOGIN_URL = "/api/auth/login";

    @Test
    public void whenAcceptLanguageIsSpanish_thenReturnsSpanishMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "es")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("El nombre de usuario es obligatorio"))
                .andExpect(jsonPath("$.password").value("La contraseña es obligatoria"));
    }

    @Test
    public void whenAcceptLanguageIsEnglish_thenReturnsEnglishMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Username is required"))
                .andExpect(jsonPath("$.password").value("Password is required"));
    }

    @Test
    public void whenAcceptLanguageIsGerman_thenReturnsGermanMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "de")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Der Benutzername ist erforderlich"))
                .andExpect(jsonPath("$.password").value("Das Passwort ist erforderlich"));
    }

    @Test
    public void whenAcceptLanguageIsFrench_thenReturnsFrenchMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "fr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Le nom d'utilisateur est obligatoire"))
                .andExpect(jsonPath("$.password").value("Le mot de passe est obligatoire"));
    }

    @Test
    public void whenAcceptLanguageIsItalian_thenReturnsItalianMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "it")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Il nome utente è obbligatorio"))
                .andExpect(jsonPath("$.password").value("La password è obbligatoria"));
    }

    @Test
    public void whenAcceptLanguageIsPortuguese_thenReturnsPortugueseMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "pt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("O nome de utilizador é obrigatório"))
                .andExpect(jsonPath("$.password").value("A palavra-passe é obrigatória"));
    }

    @Test
    public void whenAcceptLanguageIsCatalan_thenReturnsCatalanMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "ca")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("El nom d'usuari és obligatori"))
                .andExpect(jsonPath("$.password").value("La contrasenya és obligatòria"));
    }

    @Test
    public void whenAcceptLanguageIsMissing_thenFallsBackToSpanish() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("El nombre de usuario es obligatorio"));
    }

    @Test
    public void whenAcceptLanguageIsBasque_thenReturnsBasqueMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "eu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Erabiltzaile izena beharrezkoa da"))
                .andExpect(jsonPath("$.password").value("Pasahitza beharrezkoa da"));
    }

    @Test
    public void whenAcceptLanguageIsGalician_thenReturnsGalicianMessages() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "gl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("O nome de usuario é obrigatorio"))
                .andExpect(jsonPath("$.password").value("O contrasinal é obrigatorio"));
    }

    @Test
    public void whenBadCredentialsAndAcceptLanguageIsEnglish_thenReturnsEnglishErrorMessage() throws Exception {
        String invalidLoginJson = "{\"name\":\"wronguser\",\"password\":\"wrongpass\"}";

        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidLoginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.message").value("Invalid credentials. Please check your username and password."));
    }

    @Test
    public void whenBadCredentialsAndAcceptLanguageIsSpanish_thenReturnsSpanishErrorMessage() throws Exception {
        String invalidLoginJson = "{\"name\":\"wronguser\",\"password\":\"wrongpass\"}";

        mockMvc.perform(post(LOGIN_URL)
                .header("Accept-Language", "es")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidLoginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales no válidas. Verifique su usuario y contraseña."));
    }
}
