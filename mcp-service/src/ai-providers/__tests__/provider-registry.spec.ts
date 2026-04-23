import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { ProviderRegistry } from '../provider-registry';
import { OpenAiCompatibleProvider } from '../openai-compatible.provider';
import { AnthropicProvider } from '../anthropic.provider';

describe('ProviderRegistry', () => {
  const createRegistry = async (
    envOverrides: Record<string, string | undefined> = {},
  ): Promise<ProviderRegistry> => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProviderRegistry,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn((key: string) => envOverrides[key]),
          },
        },
      ],
    }).compile();

    return module.get<ProviderRegistry>(ProviderRegistry);
  };

  it('loads default providers when PROVIDER_REGISTRY is not set', async () => {
    const registry = await createRegistry({});

    const providers = registry.getRegisteredProviders();
    expect(providers).toContain('OPENAI');
    expect(providers).toContain('DEEPSEEK');
    expect(providers).toContain('GROQ');
    expect(providers).toContain('ANTHROPIC');
    expect(providers).toHaveLength(4);
  });

  it('parses PROVIDER_REGISTRY from env', async () => {
    const customConfig = JSON.stringify({
      MY_PROVIDER: {
        type: 'openai-compatible',
        baseUrl: 'https://custom.api.com',
      },
    });

    const registry = await createRegistry({ PROVIDER_REGISTRY: customConfig });

    expect(registry.getRegisteredProviders()).toEqual(['MY_PROVIDER']);
  });

  it('falls back to defaults on invalid JSON', async () => {
    const registry = await createRegistry({ PROVIDER_REGISTRY: 'not-json' });

    expect(registry.getRegisteredProviders()).toContain('OPENAI');
  });

  it('falls back to defaults when parsed value is not an object', async () => {
    const registry = await createRegistry({
      PROVIDER_REGISTRY: '"just a string"',
    });

    expect(registry.getRegisteredProviders()).toContain('OPENAI');
  });

  describe('getProvider', () => {
    let registry: ProviderRegistry;

    beforeEach(async () => {
      registry = await createRegistry({});
    });

    it('returns OpenAiCompatibleProvider for OPENAI', () => {
      const provider = registry.getProvider('OPENAI', 'test-key');
      expect(provider).toBeInstanceOf(OpenAiCompatibleProvider);
    });

    it('returns OpenAiCompatibleProvider for GROQ', () => {
      const provider = registry.getProvider('GROQ', 'test-key');
      expect(provider).toBeInstanceOf(OpenAiCompatibleProvider);
    });

    it('returns OpenAiCompatibleProvider for DEEPSEEK', () => {
      const provider = registry.getProvider('DEEPSEEK', 'test-key');
      expect(provider).toBeInstanceOf(OpenAiCompatibleProvider);
    });

    it('returns AnthropicProvider for ANTHROPIC', () => {
      const provider = registry.getProvider('ANTHROPIC', 'test-key');
      expect(provider).toBeInstanceOf(AnthropicProvider);
    });

    it('throws for unregistered provider', () => {
      expect(() => registry.getProvider('UNKNOWN', 'key')).toThrow(
        /Provider "UNKNOWN" is not registered/,
      );
    });

    it('includes available providers in error message', () => {
      expect(() => registry.getProvider('UNKNOWN', 'key')).toThrow(
        /Available: OPENAI, DEEPSEEK, GROQ, ANTHROPIC/,
      );
    });
  });
});
