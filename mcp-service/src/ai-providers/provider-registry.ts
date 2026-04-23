import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ILlmProvider } from '../common/interfaces/llm-provider.interface';
import { ProviderRegistryConfig } from './interfaces/provider-config.interface';
import { OpenAiCompatibleProvider } from './openai-compatible.provider';
import { AnthropicProvider } from './anthropic.provider';

/**
 * Default provider configuration.
 * Used if PROVIDER_REGISTRY is not defined in .env.
 */
const DEFAULT_PROVIDERS: ProviderRegistryConfig = {
  OPENAI: { type: 'openai-compatible' },
  DEEPSEEK: {
    type: 'openai-compatible',
    baseUrl: 'https://api.deepseek.com',
  },
  GROQ: {
    type: 'openai-compatible',
    baseUrl: 'https://api.groq.com/openai/v1',
  },
  ANTHROPIC: { type: 'anthropic' },
};

@Injectable()
export class ProviderRegistry {
  private readonly logger = new Logger(ProviderRegistry.name);
  private readonly configs: ProviderRegistryConfig;

  constructor(private readonly configService: ConfigService) {
    const raw = this.configService.get<string>('PROVIDER_REGISTRY');
    if (raw) {
      try {
        const parsed: unknown = JSON.parse(raw);
        if (typeof parsed === 'object' && parsed !== null) {
          this.configs = parsed as ProviderRegistryConfig;
          this.logger.log(
            `Loaded ${Object.keys(this.configs).length} provider(s) from PROVIDER_REGISTRY env`,
          );
        } else {
          this.logger.warn(
            'PROVIDER_REGISTRY is not a valid object, using defaults',
          );
          this.configs = DEFAULT_PROVIDERS;
          this.logger.log(
            `Loaded ${Object.keys(this.configs).length} default provider(s)`,
          );
        }
      } catch {
        this.logger.warn('Failed to parse PROVIDER_REGISTRY, using defaults');
        this.configs = DEFAULT_PROVIDERS;
      }
    } else {
      this.configs = DEFAULT_PROVIDERS;
      this.logger.log(
        `Using default provider registry with ${Object.keys(this.configs).length} provider(s)`,
      );
    }
  }

  /**
   * Creates an instance of ILlmProvider for the requested provider.
   * @param providerName - Provider name (e.g., 'OPENAI', 'GROQ', 'ANTHROPIC')
   * @param apiKey - API key for the provider (comes from request, decrypted by Java backend)
   */
  getProvider(providerName: string, apiKey: string): ILlmProvider {
    const config = this.configs[providerName];
    if (!config) {
      const available = Object.keys(this.configs).join(', ');
      throw new Error(
        `Provider "${providerName}" is not registered. Available: ${available}`,
      );
    }

    switch (config.type) {
      case 'openai-compatible':
        return new OpenAiCompatibleProvider(apiKey, config.baseUrl);
      case 'anthropic':
        return new AnthropicProvider(apiKey);
      default: {
        const providerType =
          typeof config === 'object' && config !== null && 'type' in config
            ? String(config.type)
            : 'unknown';
        throw new Error(
          `Unknown provider type: "${providerType}" for provider "${providerName}"`,
        );
      }
    }
  }

  /**
   * Returns the list of registered providers (useful for health check or debugging).
   */
  getRegisteredProviders(): string[] {
    return Object.keys(this.configs);
  }
}
