import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetOrdersBulkTool implements IMcpTool {
  readonly name = 'get_orders_bulk';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Recupera multiples pedidos en una sola llamada mediante una lista de IDs. Usala para obtener datos de varios pedidos simultaneamente.',
    inputSchema: {
      type: 'object',
      properties: {
        ids: {
          type: 'array',
          items: { type: 'number' },
          description: 'Lista de IDs de pedidos',
        },
      },
      required: ['ids'],
    },
  };
  readonly schema = z.object({
    ids: z.array(z.number()),
  });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { ids } = this.schema.parse(args);
    return client.post(`${MCP_BASE}/bulk/orders`, ids, jwt);
  }
}
