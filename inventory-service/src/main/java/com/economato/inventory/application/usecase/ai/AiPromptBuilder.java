package com.economato.inventory.application.usecase.ai;

import com.economato.inventory.application.usecase.mcp.mcp.McpUtilityService;
import com.economato.inventory.infrastructure.config.ai.ai.AiChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiPromptBuilder {

    private final McpUtilityService mcpUtilityService;
    private final AiChatProperties aiChatProperties;

    public String buildSystemPrompt(String userName, String language) {
        var systemContext = mcpUtilityService != null ? mcpUtilityService.getSystemContext() : null;
        String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String normalizedName = userName == null || userName.isBlank() ? "compa" : userName.trim();
        String normalizedLanguage = language == null || language.isBlank() ? aiChatProperties.getDefaultLanguage()
                : language.trim();

        long totalProducts = systemContext != null ? systemContext.getTotalProducts() : 0L;
        long pendingOrders = systemContext != null ? systemContext.getPendingOrdersCount() : 0L;
        long totalRecipes = systemContext != null ? systemContext.getTotalRecipes() : 0L;
        long activeAlerts = systemContext != null ? systemContext.getActiveAlertsCount() : 0L;

        return String.format(Locale.ROOT,
                aiChatProperties.getSystemPromptTemplate(),
                normalizedName,
                normalizedName,
                formattedDate,
                totalProducts,
                pendingOrders,
                totalRecipes,
                activeAlerts,
                normalizedLanguage);
    }
}
