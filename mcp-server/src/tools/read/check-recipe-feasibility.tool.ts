import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class CheckRecipeFeasibilityTool implements IMcpTool {
  readonly name = 'check_recipe_feasibility';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Verifica si hay stock suficiente para cocinar un numero determinado de porciones de una receta. Requiere ID y numero de porciones.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'ID de la receta' },
        portions: {
          type: 'number',
          description: 'Numero de porciones a cocinar',
        },
      },
      required: ['id', 'portions'],
    },
  };
  readonly schema = z.object({ id: z.number(), portions: z.number() });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { id, portions } = this.schema.parse(args);
    return client.get(`${MCP_BASE}/recipes/${id}/feasibility`, jwt, {
      portions,
    });
  }
}
