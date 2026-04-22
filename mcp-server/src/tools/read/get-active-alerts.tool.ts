import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetActiveAlertsTool implements IMcpTool {
  readonly name = 'get_active_alerts';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Obtiene todas las alertas operativas del sistema (stock bajo, pedidos retrasados, etc.).',
    inputSchema: { type: 'object', properties: {}, required: [] },
  };
  readonly schema = z.object({});

  async execute(
    _args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    return client.get(`${MCP_BASE}/alerts/active`, jwt);
  }
}
