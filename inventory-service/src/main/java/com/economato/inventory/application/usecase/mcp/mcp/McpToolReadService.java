package com.economato.inventory.application.usecase.mcp.mcp;

import com.economato.inventory.application.dto.stock.mcp.McpAlertDto;
import com.economato.inventory.application.dto.mcp.mcp.McpBatchDto;
import com.economato.inventory.application.dto.crisis.mcp.McpCrisisDto;
import com.economato.inventory.application.dto.mcp.mcp.McpExpiringBatchDto;
import com.economato.inventory.application.dto.mcp.mcp.McpFeasibilityDto;
import com.economato.inventory.application.dto.ledger.mcp.McpLedgerEntryDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDeepDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDto;
import com.economato.inventory.application.dto.product.mcp.McpProductDeepDto;
import com.economato.inventory.application.dto.product.mcp.McpSupplierDeepDto;
import com.economato.inventory.application.dto.weeklyplan.mcp.McpWeeklyPlanDeepDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpToolReadService {

    private final McpProductReader mcpProductReader;
    private final McpLedgerReader mcpLedgerReader;
    private final McpOrderReader mcpOrderReader;

    public McpProductDeepDto getProductDeep(Integer productId) {
        return mcpProductReader.getProductDeep(productId);
    }

    public McpRecipeDeepDto getRecipeDeep(Integer recipeId) {
        return mcpOrderReader.getRecipeDeep(recipeId);
    }

    public McpFeasibilityDto checkFeasibility(Integer recipeId, BigDecimal portions) {
        return mcpOrderReader.checkFeasibility(recipeId, portions);
    }

    public McpWeeklyPlanDeepDto getCurrentWeeklyPlanDeep() {
        return mcpOrderReader.getCurrentWeeklyPlanDeep();
    }

    public List<McpBatchDto> getProductBatches(Integer productId) {
        return mcpProductReader.getProductBatches(productId);
    }

    public List<McpLedgerEntryDto> getProductLedger(Integer productId, int limit) {
        return mcpLedgerReader.getProductLedger(productId, limit);
    }

    public List<BigDecimal> getProductForecast(Integer productId) {
        return mcpProductReader.getProductForecast(productId);
    }

    public List<BigDecimal> getProductConsumptionHistory(Integer productId) {
        return mcpProductReader.getProductConsumptionHistory(productId);
    }

    public McpSupplierDeepDto getSupplierDeep(Integer supplierId) {
        return mcpProductReader.getSupplierDeep(supplierId);
    }

    public List<McpCrisisDto> getActiveCrises() {
        return mcpLedgerReader.getActiveCrises();
    }

    public List<McpExpiringBatchDto> getExpiringSoon(int days) {
        return mcpProductReader.getExpiringSoon(days);
    }

    public List<McpAlertDto> getActiveAlerts() {
        return mcpProductReader.getActiveAlerts();
    }

    public List<McpRecipeDto> getRecipesByAllergenExclusion(List<String> excludeAllergens) {
        return mcpOrderReader.getRecipesByAllergenExclusion(excludeAllergens);
    }

    public int getDefaultReorderHorizonDays() {
        return mcpProductReader.getDefaultReorderHorizonDays();
    }
}
