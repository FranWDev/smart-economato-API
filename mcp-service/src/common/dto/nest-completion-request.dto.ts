import { IsNotEmpty, IsOptional, IsString } from 'class-validator';

export class NestCompletionRequestDto {
  /**
  * System prompt con personalidad e instrucciones del asistente.
  */
  @IsString()
  @IsNotEmpty()
  systemPrompt: string;

  /**
  * Contexto comprimido con historial, intents, entities y pregunta del usuario.
   * Generado por el SemanticMemoryGraphService del backend Java.
   */
  @IsString()
  @IsNotEmpty()
  compressedContext: string;

  /**
   * API key del proveedor de IA (se recibe desencriptada desde Java).
   */
  @IsString()
  @IsNotEmpty()
  apiKey: string;

  /**
   * Nombre del proveedor de IA a utilizar (ej: 'OPENAI', 'ANTHROPIC', 'DEEPSEEK', 'GROQ').
   * Debe coincidir con un proveedor registrado en PROVIDER_REGISTRY.
   */
  @IsString()
  @IsNotEmpty()
  provider: string;

  /**
   * Nombre del usuario autenticado en el sistema de inventario.
   */
  @IsString()
  @IsNotEmpty()
  userName: string;

  /**
   * Idioma del usuario (ej: "es", "en"). Ajusta el comportamiento del modelo.
   */
  @IsString()
  @IsNotEmpty()
  userLanguage: string;

  /**
   * Modelo a usar (ej: "gpt-4o", "claude-3-5-sonnet-20241022").
   */
  @IsString()
  @IsNotEmpty()
  model: string;

  /**
   * Temperatura del modelo (opcional, default 0.3 para respuestas consistentes).
   */
  @IsOptional()
  temperature?: number;
}
