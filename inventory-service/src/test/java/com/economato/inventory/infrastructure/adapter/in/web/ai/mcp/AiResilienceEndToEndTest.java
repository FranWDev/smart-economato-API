package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp;

import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.mcp.mcp.McpChatMessageRequest;
import com.economato.inventory.application.usecase.ai.AiChatService;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiStreamException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;

class AiResilienceEndToEndTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AiChatService aiChatService;

    @BeforeEach
        @SuppressWarnings("unused")
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
        void multipleFailures_nestAndRedis_gracefulDegradation() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.sendMessage(eq(100L), any(McpChatMessageRequest.class), any()))
                .thenThrow(new AiStreamException("Nest service unavailable"));

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("hola", "es"))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("error inesperado")));
    }

    @Test
    void redisDown_chatStillWorks_withoutRateLimit() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.sendMessage(eq(100L), any(McpChatMessageRequest.class), any()))
                .thenReturn(new SseEmitter(5000L));

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("hola", "es"))))
                .andExpect(status().isOk());
    }

    @Test
    void circuitBreakerRecovery_publishesEvent() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.sendMessage(eq(100L), any(McpChatMessageRequest.class), any()))
                .thenThrow(new AiStreamException("Nest down"))
                .thenReturn(new SseEmitter(5000L));

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("hola", "es"))))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("hola otra vez", "es"))))
                .andExpect(status().isOk());

        verify(aiChatService, times(2)).sendMessage(eq(100L), any(McpChatMessageRequest.class), any());
    }

        @Test
        void nestSlowResponse_timeoutHandled() throws Exception {
                String token = loginAsAdmin();
                when(aiChatService.sendMessage(eq(100L), any(McpChatMessageRequest.class), any()))
                                .thenThrow(new AiStreamException("Nest stream timeout"));

                mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(asJsonString(new McpChatMessageRequest("hola lenta", "es"))))
                                .andExpect(status().isInternalServerError())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.message", containsString("error inesperado")));
        }
}
