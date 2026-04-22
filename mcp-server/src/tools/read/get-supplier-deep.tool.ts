import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetSupplierDeepTool implements IMcpTool {
  readonly name = 'get_supplier_deep';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Obtiene informacion detallada de un proveedor, incluyendo contacto y productos que suministra. Requiere ID.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'ID del proveedor' },
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
    return client.get(`${MCP_BASE}/suppliers/${id}/deep`, jwt);
  }
}
