import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class UnifiedSearchTool implements IMcpTool {
  readonly name = 'unified_search';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Busca productos y recetas en una sola llamada usando un termino de texto libre. Devuelve listas separadas de productos y recetas que coinciden. Usala cuando el usuario mencione un ingrediente o plato por nombre y necesites resolver su ID.',
    inputSchema: {
      type: 'object',
      properties: {
        q: {
          type: 'string',
          description: 'Termino de busqueda (nombre de producto o receta)',
        },
      },
      required: ['q'],
    },
  };
  readonly schema = z.object({
    q: z.string().min(1),
  });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { q } = this.schema.parse(args);
    return client.get(`${MCP_BASE}/search`, jwt, { q });
  }
}
