package com.economato.inventory.application.dto.mcp;

public record McpStockHealthDto(
        int score,
        int productsAbovePrediction,
        int totalProducts,
        int batchesWithoutExpiryRisk,
        int totalActiveBatches,
        int alertsResolved,
        int totalAlerts
) {
}
