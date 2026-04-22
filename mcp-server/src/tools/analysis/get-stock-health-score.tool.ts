import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { ANALYSIS_BASE } from '../constants';

@Injectable()
export class GetStockHealthScoreTool implements IMcpTool {
  readonly name = 'get_stock_health_score';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Calcula un score global de salud del inventario (0-100) basado en prediccion de consumo, proximidad de caducidades y alertas activas. Incluye desglose por dimension. Usala para un diagnostico rapido del estado general del stock.',
    inputSchema: { type: 'object', properties: {}, required: [] },
  };
  readonly schema = z.object({});

  async execute(
    _args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    return client.get(`${ANALYSIS_BASE}/stock-health-score`, jwt);
  }
}
