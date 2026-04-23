import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { ANALYSIS_BASE } from '../constants';

@Injectable()
export class GetReorderSuggestionsTool implements IMcpTool {
  readonly name = 'get_reorder_suggestions';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Calcula productos con deficit proyectado segun forecast y stock actual, y propone cantidades de pedido con nivel de urgencia (LOW/MEDIUM/HIGH/CRITICAL). Usala para generar recomendaciones de reposicion automaticas.',
    inputSchema: { type: 'object', properties: {}, required: [] },
  };
  readonly schema = z.object({});

  async execute(
    _args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    return client.get(`${ANALYSIS_BASE}/reorder-suggestions`, jwt);
  }
}
