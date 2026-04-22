import { INestApplication } from '@nestjs/common';
import { ProviderRegistry } from '../src/ai-providers/provider-registry';
import {
  ILlmProvider,
  LlmCompletionParams,
  LlmEvent,
} from '../src/common/interfaces/llm-provider.interface';
import request from 'supertest';
import { createTestApp } from './helpers/create-test-app';
import {
  createMockProviderRegistry,
  parseSseResponse,
} from './helpers/mock-providers';

process.env.SERVICE_KEY = 'test-key-completion-tools-e2e';

describe('Completion Tools SSE (e2e)', () => {
  const validBody = {
    systemPrompt: 'Test system prompt',
    compressedContext: 'tool flow context',
    apiKey: 'provider-api-key',
    provider: 'OPENAI',
    userName: 'tester',
    userLanguage: 'es',
    model: 'gpt-4o',
  };

  it('emits tool_called, tool_result, token, done in order', async () => {
    const providerRegistryMock = createMockProviderRegistry([
      {
        type: 'tool_call',
        toolCallId: 'tc_1',
        toolName: 'get_product_deep',
        arguments: { id: 1 },
      },
      {
        type: 'tool_result',
        toolCallId: 'tc_1',
        result: { id: 1, name: 'Product' },
      },
      { type: 'text', content: 'resultado listo' },
      {
        type: 'done',
        fullText: 'resultado listo',
        inputTokens: 20,
        outputTokens: 7,
      },
    ]);

    const app = await createTestApp({ providerRegistryMock });

    const response = await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(validBody)
      .expect(200);

    const events = parseSseResponse(response.text);
    expect(events.map((event) => event.event)).toEqual([
      'tool_called',
      'tool_result',
      'token',
      'done',
    ]);

    await app.close();
  });

  it('emits error when executeTool is called with an unregistered tool', async () => {
    const provider: ILlmProvider = {
      async *streamCompletion(
        _params: LlmCompletionParams,
        _tools: Array<Record<string, unknown>>,
        executeTool: (
          name: string,
          args: Record<string, unknown>,
        ) => Promise<unknown>,
      ): AsyncIterable<LlmEvent> {
        try {
          await executeTool('nonexistent_tool', {});
        } catch (error: unknown) {
          yield {
            type: 'error',
            message:
              error instanceof Error ? error.message : 'Unknown tool error',
          };
        }
      },
    };

    const providerRegistryMock: Pick<ProviderRegistry, 'getProvider'> = {
      getProvider: jest.fn().mockReturnValue(provider),
    };

    const app: INestApplication = await createTestApp({ providerRegistryMock });

    const response = await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(validBody)
      .expect(200);

    const events = parseSseResponse(response.text);
    expect(events).toHaveLength(1);
    expect(events[0]?.event).toBe('error');
    expect(String(events[0]?.data)).toContain('not found in registry');

    await app.close();
  });

  it('emits error when executeTool fails Zod validation', async () => {
    const provider: ILlmProvider = {
      async *streamCompletion(
        _params: LlmCompletionParams,
        _tools: Array<Record<string, unknown>>,
        executeTool: (
          name: string,
          args: Record<string, unknown>,
        ) => Promise<unknown>,
      ): AsyncIterable<LlmEvent> {
        try {
          await executeTool('get_product_deep', { id: 'not-a-number' });
        } catch (error: unknown) {
          yield {
            type: 'error',
            message:
              error instanceof Error ? error.message : 'Validation error',
          };
        }
      },
    };

    const providerRegistryMock: Pick<ProviderRegistry, 'getProvider'> = {
      getProvider: jest.fn().mockReturnValue(provider),
    };

    const app: INestApplication = await createTestApp({ providerRegistryMock });

    const response = await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(validBody)
      .expect(200);

    const events = parseSseResponse(response.text);
    expect(events).toHaveLength(1);
    expect(events[0]?.event).toBe('error');
    expect(String(events[0]?.data)).toMatch(/expected\s+number|invalid_type/i);

    await app.close();
  });
});
