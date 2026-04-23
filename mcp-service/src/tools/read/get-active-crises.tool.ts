import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetActiveCrisesTool implements IMcpTool {
  readonly name = 'get_active_crises';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Lista las crisis abiertas (desabastecimientos criticos o alertas sanitarias) que requieren atencion inmediata.',
    inputSchema: { type: 'object', properties: {}, required: [] },
  };
  readonly schema = z.object({});

  async execute(
    _args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    return client.get(`${MCP_BASE}/crises/active`, jwt);
  }
}
