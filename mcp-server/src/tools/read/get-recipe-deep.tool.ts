import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetRecipeDeepTool implements IMcpTool {
  readonly name = 'get_recipe_deep';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Obtiene el detalle completo de una receta incluyendo ingredientes, alergenos y pasos de preparacion. Requiere ID.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'ID de la receta' },
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
    return client.get(`${MCP_BASE}/recipes/${id}/deep`, jwt);
  }
}
