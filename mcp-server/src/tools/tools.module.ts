import { Module } from '@nestjs/common';
import { IMcpTool } from '../common/interfaces/mcp-tool.interface';

import { GetProductDeepTool } from './read/get-product-deep.tool';
import { GetProductBatchesTool } from './read/get-product-batches.tool';
import { GetProductForecastTool } from './read/get-product-forecast.tool';
import { GetProductConsumptionHistoryTool } from './read/get-product-consumption-history.tool';
import { GetProductLedgerTool } from './read/get-product-ledger.tool';
import { GetRecipeDeepTool } from './read/get-recipe-deep.tool';
import { CheckRecipeFeasibilityTool } from './read/check-recipe-feasibility.tool';
import { GetRecipesByAllergenExclusionTool } from './read/get-recipes-by-allergen-exclusion.tool';
import { GetCurrentWeeklyPlanDeepTool } from './read/get-current-weekly-plan-deep.tool';
import { GetSupplierDeepTool } from './read/get-supplier-deep.tool';
import { GetActiveCrisesTool } from './read/get-active-crises.tool';
import { GetExpiringSoonTool } from './read/get-expiring-soon.tool';
import { GetActiveAlertsTool } from './read/get-active-alerts.tool';

import { GetReorderSuggestionsTool } from './analysis/get-reorder-suggestions.tool';
import { GetWasteRiskTool } from './analysis/get-waste-risk.tool';
import { GetMenuOptimizerTool } from './analysis/get-menu-optimizer.tool';
import { GetCostBreakdownTool } from './analysis/get-cost-breakdown.tool';
import { GetStockHealthScoreTool } from './analysis/get-stock-health-score.tool';

import { GetSystemContextTool } from './utility/get-system-context.tool';
import { UnifiedSearchTool } from './utility/unified-search.tool';
import { GetProductsFilteredTool } from './utility/get-products-filtered.tool';
import { GetPendingOrdersTool } from './utility/get-pending-orders.tool';
import { GetProductsBulkTool } from './utility/get-products-bulk.tool';
import { GetOrdersBulkTool } from './utility/get-orders-bulk.tool';
import { GetRecipesBulkTool } from './utility/get-recipes-bulk.tool';

const ALL_TOOLS = [
  GetProductDeepTool,
  GetProductBatchesTool,
  GetProductForecastTool,
  GetProductConsumptionHistoryTool,
  GetProductLedgerTool,
  GetRecipeDeepTool,
  CheckRecipeFeasibilityTool,
  GetRecipesByAllergenExclusionTool,
  GetCurrentWeeklyPlanDeepTool,
  GetSupplierDeepTool,
  GetActiveCrisesTool,
  GetExpiringSoonTool,
  GetActiveAlertsTool,
  GetReorderSuggestionsTool,
  GetWasteRiskTool,
  GetMenuOptimizerTool,
  GetCostBreakdownTool,
  GetStockHealthScoreTool,
  GetSystemContextTool,
  UnifiedSearchTool,
  GetProductsFilteredTool,
  GetPendingOrdersTool,
  GetProductsBulkTool,
  GetOrdersBulkTool,
  GetRecipesBulkTool,
];

@Module({
  providers: [
    ...ALL_TOOLS,
    {
      provide: 'TOOL_REGISTRY',
      useFactory: (...tools: IMcpTool[]) => {
        const map = new Map<string, IMcpTool>();
        for (const tool of tools) {
          if (map.has(tool.name)) {
            throw new Error(`Duplicate tool name: "${tool.name}"`);
          }
          map.set(tool.name, tool);
        }
        return map;
      },
      inject: ALL_TOOLS,
    },
  ],
  exports: ['TOOL_REGISTRY'],
})
export class ToolsModule {}
