/**
 * Parámetros que recibe un provider para generar una completion.
 * Desacoplado del DTO HTTP para que los providers no dependan de la forma del request.
 */
export interface LlmCompletionParams {
  /** System prompt con personalidad e instrucciones del asistente */
  systemPrompt: string;
  /** Contexto comprimido con historial, intents, entities y pregunta del usuario */
  compressedContext: string;
  /** Modelo a usar (ej: "gpt-4o", "claude-sonnet-4-20250514") */
  model: string;
  /** Temperatura del modelo (default 0.3) */
  temperature?: number;
}

/**
 * Evento emitido por el provider de LLM durante el streaming.
 */
export type LlmEvent =
  | { type: 'text'; content: string }
  | {
      type: 'tool_call';
      toolCallId: string;
      toolName: string;
      arguments: Record<string, unknown>;
    }
  | { type: 'tool_result'; toolCallId: string; result: unknown }
  | {
      type: 'done';
      fullText: string;
      inputTokens: number;
      outputTokens: number;
    }
  | { type: 'error'; message: string };

/**
 * Interfaz que deben implementar todos los adaptadores de LLM.
 */
export interface ILlmProvider {
  /**
   * Inicia el streaming de completion.
   * Maneja el bucle interno de tools si es necesario.
   */
  streamCompletion(
    params: LlmCompletionParams,
    tools: Array<Record<string, unknown>>,
    executeTool: (
      name: string,
      args: Record<string, unknown>,
    ) => Promise<unknown>,
  ): AsyncIterable<LlmEvent>;
}
