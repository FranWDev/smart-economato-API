import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetProductForecastTool implements IMcpTool {
  readonly name = 'get_product_forecast';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Obtiene la prediccion de consumo proyectada para un producto basada en el historial y planes actuales. Requiere ID.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'ID del producto' },
      },
      required: ['id'],
    },
  };
  readonly schema = z.object({ id: z.number() });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { id } = this.schema.parse(args);
    return client.get(`${MCP_BASE}/products/${id}/forecast`, jwt);
  }
}
