import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetPendingOrdersTool implements IMcpTool {
  readonly name = 'get_pending_orders';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Obtiene los pedidos que estan en estado PENDING o ORDERED. Devuelve lista con ID, estado, importe, items, proveedor y fecha. Usala para conocer pedidos en curso.',
    inputSchema: { type: 'object', properties: {}, required: [] },
  };
  readonly schema = z.object({});

  async execute(
    _args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    return client.get(`${MCP_BASE}/orders/pending`, jwt);
  }
}
