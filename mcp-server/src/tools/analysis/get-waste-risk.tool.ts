import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { ANALYSIS_BASE } from '../constants';

@Injectable()
export class GetWasteRiskTool implements IMcpTool {
  readonly name = 'get_waste_risk';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Lista lotes proximos a caducar junto con sugerencias de recetas que podrian consumir esos ingredientes para reducir desperdicio alimentario. Usala para estrategias anti-merma.',
    inputSchema: { type: 'object', properties: {}, required: [] },
  };
  readonly schema = z.object({});

  async execute(
    _args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    return client.get(`${ANALYSIS_BASE}/waste-risk`, jwt);
  }
}
