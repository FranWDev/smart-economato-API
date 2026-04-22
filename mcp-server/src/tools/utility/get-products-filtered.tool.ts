import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetProductsFilteredTool implements IMcpTool {
  readonly name = 'get_products_filtered';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Lista productos con filtros opcionales de precio minimo y maximo. Usala para explorar el catalogo de productos o filtrar por rango de precio.',
    inputSchema: {
      type: 'object',
      properties: {
        min_price: {
          type: 'number',
          description: 'Precio minimo (opcional)',
        },
        max_price: {
          type: 'number',
          description: 'Precio maximo (opcional)',
        },
      },
      required: [],
    },
  };
  readonly schema = z.object({
    min_price: z.number().optional(),
    max_price: z.number().optional(),
  });

  async execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    const { min_price, max_price } = this.schema.parse(args);
    const params: Record<string, number> = {};
    if (min_price !== undefined) params.minPrice = min_price;
    if (max_price !== undefined) params.maxPrice = max_price;
    return client.get(`${MCP_BASE}/products`, jwt, params);
  }
}
