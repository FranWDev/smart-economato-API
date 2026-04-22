import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { ANALYSIS_BASE } from '../constants';

@Injectable()
export class GetMenuOptimizerTool implements IMcpTool {
  readonly name = 'get_menu_optimizer';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Propone un menu semanal optimizado considerando presupuesto maximo y exclusiones de alergenos. Devuelve sugerencia de menu con dias, recetas asignadas y coste estimado. Usala para planificacion de menu inteligente.',
    inputSchema: {
      type: 'object',
      properties: {
        budget: {
          type: 'number',
          description: 'Presupuesto total estimado para la semana (opcional)',
        },
        exclude: {
          type: 'array',
          items: { type: 'string' },
          description:
            'Lista de alergenos a excluir del menu (ej: ["gluten", "lactosa"]). Opcional.',
        },
      },
      required: [],
    },
  };
  readonly schema = z.object({
    budget: z.number().optional(),
    exclude: z.array(z.string()).optional(),
  });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { budget, exclude } = this.schema.parse(args);
    const params: Record<string, unknown> = {};
    if (budget !== undefined) params.budget = budget;
    if (exclude !== undefined && exclude.length > 0) {
      params.exclude = exclude.join(',');
    }
    return client.get(`${ANALYSIS_BASE}/menu-optimizer`, jwt, params);
  }
}
