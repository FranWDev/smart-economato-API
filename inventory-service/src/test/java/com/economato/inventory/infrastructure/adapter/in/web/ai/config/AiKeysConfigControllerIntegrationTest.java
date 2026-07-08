package com.economato.inventory.infrastructure.adapter.in.web.ai.config;
import com.economato.inventory.domain.model.user.User;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.application.dto.user.request.GlobalApiKeyRequestDTO;
import com.economato.inventory.application.usecase.ai.AiKeyVaultService;
import com.economato.inventory.domain.model.ai.AiProvider;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;

class AiKeysConfigControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

        private Integer adminUserId;

    @MockitoBean
    private AiKeyVaultService aiKeyVaultService;

    @BeforeEach
    void setUp() {
        clearDatabase();
                adminUserId = userRepository.saveAndFlush(TestDataUtil.createAdminUser()).getId();
        userRepository.saveAndFlush(TestDataUtil.createRegularUser());
    }

    @Test
    void adminCanListCreateUpdateAndDeleteGlobalKeys() throws Exception {
        String token = loginAsAdmin();
        when(aiKeyVaultService.listGlobalKeys()).thenReturn(List.of(
                new AiKeyVaultService.ApiKeyMetadata(1L, AiProvider.OPENAI, "****test", true, LocalDateTime.now())
        ));

        mockMvc.perform(get("/api/config/ai-keys/")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].provider", is("OPENAI")))
                .andExpect(jsonPath("$[0].keyHint", is("****test")));

        AiKeyVaultService.ApiKeyMetadata metadataSaved = new AiKeyVaultService.ApiKeyMetadata(1L, AiProvider.OPENAI, "****1234", true, LocalDateTime.now());
        when(aiKeyVaultService.saveGlobalKey(AiProvider.OPENAI, "sk-test-1234", adminUserId)).thenReturn(metadataSaved);

        mockMvc.perform(post("/api/config/ai-keys/")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new GlobalApiKeyRequestDTO("OPENAI", "sk-test-1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("OPENAI")))
                .andExpect(jsonPath("$.keyHint", is("****1234")));

        AiKeyVaultService.ApiKeyMetadata metadataUpdated = new AiKeyVaultService.ApiKeyMetadata(1L, AiProvider.OPENAI, "****5678", true, LocalDateTime.now());
        when(aiKeyVaultService.updateGlobalKey(AiProvider.OPENAI, "sk-test-5678", adminUserId)).thenReturn(metadataUpdated);

        mockMvc.perform(put("/api/config/ai-keys/")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new GlobalApiKeyRequestDTO("OPENAI", "sk-test-5678"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("OPENAI")))
                .andExpect(jsonPath("$.keyHint", is("****5678")));

        mockMvc.perform(delete("/api/config/ai-keys/OPENAI")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

                verify(aiKeyVaultService).saveGlobalKey(AiProvider.OPENAI, "sk-test-1234", adminUserId);
                verify(aiKeyVaultService).updateGlobalKey(AiProvider.OPENAI, "sk-test-5678", adminUserId);
                verify(aiKeyVaultService).deleteGlobalKey(AiProvider.OPENAI, adminUserId);
    }

    @Test
    void nonAdminCannotManageGlobalKeys() throws Exception {
        String token = login("User", "user123");

        mockMvc.perform(get("/api/config/ai-keys/")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/config/ai-keys/")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new GlobalApiKeyRequestDTO("OPENAI", "sk-test-1234"))))
                .andExpect(status().isForbidden());
    }
}
