package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.application.dto.mcp.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpChatResponseDto;
import com.economato.inventory.application.usecase.ai.AiChatService;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;

class AiChatControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
    void listChats_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/chat/chats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listChats_withAuth_returnsServicePayload() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.listChats()).thenReturn(List.of(
                new McpChatResponseDto(100L, "Chat 1", "ACTIVE", "OPENAI", "es", LocalDateTime.now(), LocalDateTime.now(), 3)
        ));

        mockMvc.perform(get("/api/chat/chats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(100)))
                .andExpect(jsonPath("$[0].activeProvider", is("OPENAI")));
    }

    @Test
    void createChat_withAuth_delegatesToService() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.createChat(any())).thenReturn(
                new McpChatResponseDto(101L, "Ops", "ACTIVE", "OPENAI", "es", LocalDateTime.now(), LocalDateTime.now(), 0)
        );

        mockMvc.perform(post("/api/chat/chats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatCreateRequest("Ops", "OPENAI"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(101)));

        verify(aiChatService).createChat(any());
    }

    @Test
    void archiveChat_withAuth_callsService() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(delete("/api/chat/chats/77")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(aiChatService).archiveChat(77L);
    }

    @Test
        void listProviders_withAuth_work() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.listEnabledProviders()).thenReturn(List.of(Map.of("provider", "OPENAI", "model", "gpt-4o")));

        mockMvc.perform(get("/api/chat/providers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider", is("OPENAI")));
        verify(aiChatService).listEnabledProviders();
    }
}
