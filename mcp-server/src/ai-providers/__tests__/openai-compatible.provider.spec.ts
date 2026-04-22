import { OpenAiCompatibleProvider } from '../openai-compatible.provider';
import { LlmCompletionParams } from '../../common/interfaces/llm-provider.interface';

jest.mock('../../common/logging/logger', () => ({
  mcpLogger: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
  },
}));

jest.mock('openai', () => {
  const create = jest.fn();
  const OpenAI = jest.fn().mockImplementation(() => ({
    chat: {
      completions: {
        create,
      },
    },
  }));

  return {
    __esModule: true,
    default: OpenAI,
  };
});

describe('OpenAiCompatibleProvider', () => {
  let provider: OpenAiCompatibleProvider;
  let mockCreate: jest.Mock;

  const defaultParams: LlmCompletionParams = {
    systemPrompt: 'system prompt',
    compressedContext: 'You are a helpful assistant.',
    model: 'gpt-4o',
    temperature: 0.3,
  };

  beforeEach(() => {
    jest.clearAllMocks();

    provider = new OpenAiCompatibleProvider('test-api-key');

    const openAiModule = jest.requireMock('openai') as unknown as {
      default: jest.Mock;
    };
    const instance = openAiModule.default.mock.results[0]?.value as {
      chat: { completions: { create: jest.Mock } };
    };
    mockCreate = instance.chat.completions.create;
  });

  it('yields text events and done event for simple response', async () => {
    // eslint-disable-next-line @typescript-eslint/require-await
    const mockStream = (async function* () {
      yield { choices: [{ delta: { content: 'Hello' } }], usage: null };
      yield { choices: [{ delta: { content: ' world' } }], usage: null };
      yield {
        choices: [{ delta: {} }],
        usage: { prompt_tokens: 10, completion_tokens: 5 },
      };
    })();

    mockCreate.mockResolvedValue(mockStream);

    const events: Array<Record<string, unknown>> = [];
    for await (const event of provider.streamCompletion(
      defaultParams,
      [],
      jest.fn(),
    )) {
      events.push(event as unknown as Record<string, unknown>);
    }

    expect(events.filter((e) => e.type === 'text')).toHaveLength(2);
    expect(events[0]).toEqual({ type: 'text', content: 'Hello' });
    expect(events[1]).toEqual({ type: 'text', content: ' world' });

    const firstCallArgs = mockCreate.mock.calls[0]?.[0] as {
      messages?: Array<{ role: string; content: unknown }>;
    };
    expect(firstCallArgs?.messages).toEqual([
      { role: 'system', content: 'system prompt' },
      { role: 'user', content: 'You are a helpful assistant.' },
    ]);

    const doneEvent = events.find((e) => e.type === 'done');
    expect(doneEvent).toMatchObject({
      type: 'done',
      fullText: 'Hello world',
      inputTokens: 10,
      outputTokens: 5,
    });
  });

  it('yields tool_call and tool_result, then continues', async () => {
    // eslint-disable-next-line @typescript-eslint/require-await
    const firstStream = (async function* () {
      yield {
        choices: [
          {
            delta: {
              tool_calls: [
                {
                  index: 0,
                  id: 'call_123',
                  function: { name: 'get_product_deep', arguments: '{"id":' },
                },
              ],
            },
          },
        ],
        usage: null,
      };
      yield {
        choices: [
          {
            delta: {
              tool_calls: [
                {
                  index: 0,
                  id: '',
                  function: { name: '', arguments: '1}' },
                },
              ],
            },
          },
        ],
        usage: null,
      };
      yield {
        choices: [{ delta: {} }],
        usage: { prompt_tokens: 20, completion_tokens: 10 },
      };
    })();

    // eslint-disable-next-line @typescript-eslint/require-await
    const secondStream = (async function* () {
      yield {
        choices: [{ delta: { content: 'Product info: ...' } }],
        usage: null,
      };
      yield {
        choices: [{ delta: {} }],
        usage: { prompt_tokens: 30, completion_tokens: 15 },
      };
    })();

    mockCreate
      .mockResolvedValueOnce(firstStream)
      .mockResolvedValueOnce(secondStream);

    const mockExecuteTool = jest
      .fn<Promise<unknown>, [string, Record<string, unknown>]>()
      .mockResolvedValue({ id: 1, name: 'Test Product' });

    const events: Array<Record<string, unknown>> = [];
    const tools = [
      {
        name: 'get_product_deep',
        description: 'test',
        inputSchema: { type: 'object', properties: {}, required: [] },
      },
    ];

    for await (const event of provider.streamCompletion(
      defaultParams,
      tools,
      mockExecuteTool,
    )) {
      events.push(event as unknown as Record<string, unknown>);
    }

    const firstCallArgs = mockCreate.mock.calls[0]?.[0] as {
      messages?: Array<{ role: string; content: unknown }>;
    };
    expect(firstCallArgs?.messages?.[0]).toEqual({
      role: 'system',
      content: 'system prompt',
    });
    expect(firstCallArgs?.messages?.[1]).toEqual({
      role: 'user',
      content: 'You are a helpful assistant.',
    });

    expect(events.find((e) => e.type === 'tool_call')).toMatchObject({
      type: 'tool_call',
      toolCallId: 'call_123',
      toolName: 'get_product_deep',
    });
    expect(events.find((e) => e.type === 'tool_result')).toMatchObject({
      type: 'tool_result',
      toolCallId: 'call_123',
      result: { id: 1, name: 'Test Product' },
    });
    expect(mockExecuteTool).toHaveBeenCalledWith('get_product_deep', { id: 1 });

    const doneEvent = events.find((e) => e.type === 'done');
    expect(doneEvent).toMatchObject({
      type: 'done',
      fullText: 'Product info: ...',
      inputTokens: 50,
      outputTokens: 25,
    });
  });

  it('yields error event on SDK failure', async () => {
    mockCreate.mockRejectedValue(new Error('API rate limit exceeded'));

    const events: Array<Record<string, unknown>> = [];
    for await (const event of provider.streamCompletion(
      defaultParams,
      [],
      jest.fn(),
    )) {
      events.push(event as unknown as Record<string, unknown>);
    }

    expect(events.find((e) => e.type === 'error')).toMatchObject({
      type: 'error',
      message: 'API rate limit exceeded',
    });
  });

  it('continues loop when tool execution fails', async () => {
    // eslint-disable-next-line @typescript-eslint/require-await
    const firstStream = (async function* () {
      yield {
        choices: [
          {
            delta: {
              tool_calls: [
                {
                  index: 0,
                  id: 'call_err',
                  function: {
                    name: 'get_product_deep',
                    arguments: '{"id":999}',
                  },
                },
              ],
            },
          },
        ],
        usage: null,
      };
      yield {
        choices: [{ delta: {} }],
        usage: { prompt_tokens: 10, completion_tokens: 5 },
      };
    })();

    // eslint-disable-next-line @typescript-eslint/require-await
    const secondStream = (async function* () {
      yield {
        choices: [{ delta: { content: 'Sorry, product not found.' } }],
        usage: null,
      };
      yield {
        choices: [{ delta: {} }],
        usage: { prompt_tokens: 15, completion_tokens: 8 },
      };
    })();

    mockCreate
      .mockResolvedValueOnce(firstStream)
      .mockResolvedValueOnce(secondStream);

    const mockExecuteTool = jest
      .fn<Promise<unknown>, [string, Record<string, unknown>]>()
      .mockRejectedValue(new Error('Product not found'));

    const events: Array<Record<string, unknown>> = [];
    const tools = [
      {
        name: 'get_product_deep',
        description: 'test',
        inputSchema: { type: 'object', properties: {}, required: [] },
      },
    ];

    for await (const event of provider.streamCompletion(
      defaultParams,
      tools,
      mockExecuteTool,
    )) {
      events.push(event as unknown as Record<string, unknown>);
    }

    const doneEvent = events.find((e) => e.type === 'done') as
      | { fullText?: string }
      | undefined;
    expect(doneEvent).toBeDefined();
    expect(doneEvent?.fullText).toContain('Sorry, product not found.');
  });
});
