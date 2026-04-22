import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetProductsBulkTool implements IMcpTool {
  readonly name = 'get_products_bulk';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Recupera multiples productos en una sola llamada mediante una lista de IDs numericos y/o codigos de producto. Usala para obtener datos de varios productos a la vez sin hacer llamadas individuales.',
    inputSchema: {
      type: 'object',
      properties: {
        ids: {
          type: 'array',
          items: { type: 'number' },
          description: 'Lista de IDs numericos de productos (opcional)',
        },
        codes: {
          type: 'array',
          items: { type: 'string' },
          description: 'Lista de codigos de productos (opcional)',
        },
      },
      required: [],
    },
  };
  readonly schema = z.object({
    ids: z.array(z.number()).optional(),
    codes: z.array(z.string()).optional(),
  });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { ids, codes } = this.schema.parse(args);
    return client.post(
      `${MCP_BASE}/bulk/products`,
      {
        ids: ids ?? [],
        codes: codes ?? [],
      },
      jwt,
    );
  }
}
