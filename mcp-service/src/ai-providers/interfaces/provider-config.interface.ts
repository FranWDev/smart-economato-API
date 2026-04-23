/**
 * Configuración de un proveedor de IA registrado.
 * - 'openai-compatible': Cualquier API que siga el formato de OpenAI (OpenAI, DeepSeek, Groq, Together, Mistral, etc.)
 * - 'anthropic': API de Anthropic (formato diferente con tool_use content blocks)
 */
export interface ProviderConfig {
  type: 'openai-compatible' | 'anthropic';
  /** URL base de la API. Solo necesario para proveedores openai-compatible con URL custom (ej: DeepSeek, Groq). Si no se especifica, usa la URL por defecto del SDK de OpenAI. */
  baseUrl?: string;
}

/**
 * Mapa de proveedores registrados. La key es el nombre del proveedor (ej: 'OPENAI', 'GROQ').
 */
export type ProviderRegistryConfig = Record<string, ProviderConfig>;
