import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetRecipesByAllergenExclusionTool implements IMcpTool {
  readonly name = 'get_recipes_by_allergen_exclusion';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Filtra recetas excluyendo aquellas que contengan ciertos alergenos. Usala para dietas especiales.',
    inputSchema: {
      type: 'object',
      properties: {
        exclude: {
          type: 'array',
          items: { type: 'string' },
          description: 'Lista de alergenos a excluir',
        },
      },
      required: ['exclude'],
    },
  };
  readonly schema = z.object({ exclude: z.array(z.string()) });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { exclude } = this.schema.parse(args);
    return client.get(`${MCP_BASE}/recipes/by-allergen-exclusion`, jwt, {
      exclude: exclude.join(','),
    });
  }
}
