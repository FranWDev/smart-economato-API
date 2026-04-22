import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { ANALYSIS_BASE } from '../constants';

@Injectable()
export class GetCostBreakdownTool implements IMcpTool {
  readonly name = 'get_cost_breakdown';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Calcula el coste total y desglose por producto/receta en un rango temporal dado. Devuelve totales, promedios y detalle por categoria. Usala para analisis financiero del economato.',
    inputSchema: {
      type: 'object',
      properties: {
        from: {
          type: 'string',
          description: 'Fecha inicio en formato yyyy-MM-dd',
        },
        to: {
          type: 'string',
          description: 'Fecha fin en formato yyyy-MM-dd',
        },
      },
      required: ['from', 'to'],
    },
  };
  readonly schema = z.object({
    from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
    to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { from, to } = this.schema.parse(args);
    return client.get(`${ANALYSIS_BASE}/cost-breakdown`, jwt, { from, to });
  }
}
