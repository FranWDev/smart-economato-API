import { Injectable } from '@nestjs/common';
import { z } from 'zod';
import {
  IMcpTool,
  McpToolDefinition,
} from '../../common/interfaces/mcp-tool.interface';
import { InventoryClient } from '../../mcp/inventory.client';
import { MCP_BASE } from '../constants';

@Injectable()
export class GetSystemContextTool implements IMcpTool {
  readonly name = 'get_system_context';
  readonly definition: McpToolDefinition = {
    name: this.name,
    description:
      'Devuelve un resumen global del estado del sistema: totales de productos, recetas, pedidos, alertas activas, crisis y metricas clave. Usala como primera llamada para que el LLM entienda el contexto operativo antes de responder.',
    inputSchema: { type: 'object', properties: {}, required: [] },
  };
  readonly schema = z.object({});

  async execute(
    _args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown> {
    return client.get(`${MCP_BASE}/context`, jwt);
  }
}
