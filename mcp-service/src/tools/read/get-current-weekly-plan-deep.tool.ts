import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetCurrentWeeklyPlanDeepTool implements IMcpTool {
  readonly name = 'get_current_weekly_plan_deep';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Obtiene el plan semanal actual con todos los slots de cocinado programados y su estado.',
    inputSchema: { type: 'object', properties: {}, required: [] },
  };
  readonly schema = z.object({});

  async execute(
    _args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    return client.get(`${MCP_BASE}/weekly-plan/current/deep`, jwt);
  }
}
