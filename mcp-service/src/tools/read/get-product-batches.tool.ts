import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetProductBatchesTool implements IMcpTool {
  readonly name = 'get_product_batches';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Lista todos los lotes activos de un producto con sus fechas de entrada y caducidad. Requiere ID.',
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
    return client.get(`${MCP_BASE}/products/${id}/batches`, jwt);
  }
}
