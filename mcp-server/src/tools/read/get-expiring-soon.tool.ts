import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetExpiringSoonTool implements IMcpTool {
  readonly name = 'get_expiring_soon';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Encuentra lotes que caducaran en los proximos N dias. Usala para prevenir desperdicio.',
    inputSchema: {
      type: 'object',
      properties: {
        days: { type: 'number', description: 'Dias de antelacion', default: 7 },
      },
      required: [],
    },
  };
  readonly schema = z.object({ days: z.number().optional().default(7) });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { days } = this.schema.parse(args);
    return client.get(`${MCP_BASE}/expiring-soon`, jwt, { days });
  }
}
