import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetProductLedgerTool implements IMcpTool {
  readonly name = 'get_product_ledger';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Muestra los ultimos movimientos de stock (entradas/salidas) de un producto. Requiere ID y un limite opcional.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'ID del producto' },
        limit: {
          type: 'number',
          description: 'Numero de movimientos a mostrar',
          default: 10,
        },
      },
      required: ['id'],
    },
  };
  readonly schema = z.object({
    id: z.number(),
    limit: z.number().optional().default(10),
  });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { id, limit } = this.schema.parse(args);
    return client.get(`${MCP_BASE}/products/${id}/ledger`, jwt, { limit });
  }
}
