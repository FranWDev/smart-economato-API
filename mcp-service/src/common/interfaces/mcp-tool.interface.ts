import { z } from 'zod';
import { InventoryClient } from '../../mcp/inventory.client';

/**
 * Definicion de una tool MCP para enviar al LLM.
 * Sigue el formato JSON Schema que OpenAI y Anthropic esperan.
 */
export interface McpToolDefinition {
  name: string;
  description: string;
  inputSchema: {
    type: 'object';
    properties: Record<string, unknown>;
    required: string[];
  };
}

/**
 * Interfaz que debe implementar cada tool MCP.
 * Co-localiza definicion, validacion y ejecucion.
 */
export interface IMcpTool {
  /** Nombre unico de la tool (ej: 'get_product_deep') */
  readonly name: string;
  /** Definicion completa para enviar al LLM */
  readonly definition: McpToolDefinition;
  /** Schema Zod para validar los argumentos antes de ejecutar */
  readonly schema: z.ZodSchema;
  /** Ejecuta la tool con los argumentos ya validados */
  execute(
    args: unknown,
    client: InventoryClient,
    jwt: string,
  ): Promise<unknown>;
}
