package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.economato.inventory.application.dto.mcp.McpChangeProviderRequest;
import com.economato.inventory.application.dto.mcp.McpChatCreateRequest;
import com.economato.inventory.application.dto.mcp.McpChatMessageRequest;
import com.economato.inventory.application.dto.mcp.McpChatMessageResponseDto;
import com.economato.inventory.application.dto.mcp.McpChatResponseDto;
import com.economato.inventory.application.usecase.AiChatService;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

class AiChatFlowIntegrationTest extends BaseIntegrationTest {

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
    void fullChatFlow_createChat_sendMessage_getHistory_archive() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.createChat(any())).thenReturn(new McpChatResponseDto(100L, "Ops", "ACTIVE", "OPENAI", "es", LocalDateTime.now(), LocalDateTime.now(), 0));
        when(aiChatService.sendMessage(eq(100L), any(McpChatMessageRequest.class), any())).thenReturn(new SseEmitter(5000L));
        when(aiChatService.getChatHistory(100L)).thenReturn(List.of(
                new McpChatMessageResponseDto(1L, "USER", "hola", null, null, 0, 0, LocalDateTime.now()),
                new McpChatMessageResponseDto(2L, "ASSISTANT", "respuesta", null, null, 10, 20, LocalDateTime.now())
        ));

        mockMvc.perform(post("/api/chat/chats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatCreateRequest("Ops", "OPENAI"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(100)));

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("hola", "es"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chat/chats/100/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(delete("/api/chat/chats/100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(aiChatService).createChat(any());
        verify(aiChatService).sendMessage(eq(100L), any(McpChatMessageRequest.class), any());
        verify(aiChatService).getChatHistory(100L);
        verify(aiChatService).archiveChat(100L);
    }

    @Test
    void fullChatFlow_changeProvider_midConversation() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.sendMessage(eq(100L), any(McpChatMessageRequest.class), any())).thenReturn(new SseEmitter(5000L));
        when(aiChatService.changeProvider(eq(100L), any(McpChangeProviderRequest.class)))
                .thenReturn(new McpChatResponseDto(100L, "Ops", "ACTIVE", "ANTHROPIC", "es", LocalDateTime.now(), LocalDateTime.now(), 2));

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("primero", "es"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/chat/chats/100/provider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChangeProviderRequest("ANTHROPIC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProvider", is("ANTHROPIC")));

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("segundo", "es"))))
                .andExpect(status().isOk());

        verify(aiChatService, times(2)).sendMessage(eq(100L), any(McpChatMessageRequest.class), any());
        verify(aiChatService).changeProvider(eq(100L), any(McpChangeProviderRequest.class));
    }

    @Test
    void fullChatFlow_languageDetection_updatesOnEachMessage() throws Exception {
        String token = loginAsAdmin();
        when(aiChatService.sendMessage(eq(100L), any(McpChatMessageRequest.class), any())).thenReturn(new SseEmitter(5000L));

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("hello", "en"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/chat/chats/100/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpChatMessageRequest("bonjour", "fr"))))
                .andExpect(status().isOk());

        ArgumentCaptor<McpChatMessageRequest> requestCaptor = ArgumentCaptor.forClass(McpChatMessageRequest.class);
        verify(aiChatService, times(2)).sendMessage(eq(100L), requestCaptor.capture(), any());
        List<String> languages = requestCaptor.getAllValues().stream().map(McpChatMessageRequest::language).toList();
        org.junit.jupiter.api.Assertions.assertEquals(List.of("en", "fr"), languages);
    }

}
