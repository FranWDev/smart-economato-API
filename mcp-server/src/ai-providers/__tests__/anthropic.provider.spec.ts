import { AnthropicProvider } from '../anthropic.provider';
import { LlmCompletionParams } from '../../common/interfaces/llm-provider.interface';

jest.mock('../../common/logging/logger', () => ({
  mcpLogger: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
  },
}));

jest.mock('@anthropic-ai/sdk', () => {
  const stream = jest.fn();
  const Anthropic = jest.fn().mockImplementation(() => ({
    messages: {
      stream,
    },
  }));

  return {
    __esModule: true,
    default: Anthropic,
  };
});

describe('AnthropicProvider', () => {
  let provider: AnthropicProvider;
  let mockStream: jest.Mock;

  const defaultParams: LlmCompletionParams = {
    systemPrompt: 'system prompt',
    compressedContext: 'You are a helpful assistant.',
    model: 'claude-sonnet-4-20250514',
    temperature: 0.3,
  };

  beforeEach(() => {
    jest.clearAllMocks();
    provider = new AnthropicProvider('test-api-key');

    const anthropicModule = jest.requireMock(
      '@anthropic-ai/sdk',
    ) as unknown as {
      default: jest.Mock;
    };
    const instance = anthropicModule.default.mock.results[0]?.value as {
      messages: { stream: jest.Mock };
    };
    mockStream = instance.messages.stream;
  });

  it('yields text events and done event for simple response', async () => {
    // eslint-disable-next-line @typescript-eslint/require-await
    const mockEvents = (async function* () {
      yield { type: 'message_start', message: { usage: { input_tokens: 15 } } };
      yield {
        type: 'content_block_start',
        index: 0,
        content_block: { type: 'text', text: '' },
      };
      yield {
        type: 'content_block_delta',
        index: 0,
        delta: { type: 'text_delta', text: 'Hello' },
      };
      yield {
        type: 'content_block_delta',
        index: 0,
        delta: { type: 'text_delta', text: ' world' },
      };
      yield { type: 'message_delta', usage: { output_tokens: 8 } };
    })();

    mockStream.mockReturnValue(mockEvents);

    const events: Array<Record<string, unknown>> = [];
    for await (const event of provider.streamCompletion(
      defaultParams,
      [],
      jest.fn(),
    )) {
      events.push(event as unknown as Record<string, unknown>);
    }

    const firstCallArgs = mockStream.mock.calls[0]?.[0] as {
      system?: string;
      messages?: Array<{ role: string; content: unknown }>;
    };
    expect(firstCallArgs?.system).toBe('system prompt');
    expect(firstCallArgs?.messages?.[0]).toEqual({
      role: 'user',
      content: 'You are a helpful assistant.',
    });

    expect(events.filter((e) => e.type === 'text')).toHaveLength(2);
    expect(events[0]).toEqual({ type: 'text', content: 'Hello' });

    const doneEvent = events.find((e) => e.type === 'done');
    expect(doneEvent).toMatchObject({
      type: 'done',
      fullText: 'Hello world',
      inputTokens: 15,
      outputTokens: 8,
    });
  });

  it('yields tool_call and tool_result events', async () => {
    // eslint-disable-next-line @typescript-eslint/require-await
    const firstEvents = (async function* () {
      yield { type: 'message_start', message: { usage: { input_tokens: 20 } } };
      yield {
        type: 'content_block_start',
        index: 0,
        content_block: {
          type: 'tool_use',
          id: 'tu_123',
          name: 'get_product_deep',
        },
      };
      yield {
        type: 'content_block_delta',
        index: 0,
        delta: { type: 'input_json_delta', partial_json: '{"id":' },
      };
      yield {
        type: 'content_block_delta',
        index: 0,
        delta: { type: 'input_json_delta', partial_json: '1}' },
      };
      yield { type: 'message_delta', usage: { output_tokens: 10 } };
    })();

    // eslint-disable-next-line @typescript-eslint/require-await
    const secondEvents = (async function* () {
      yield { type: 'message_start', message: { usage: { input_tokens: 30 } } };
      yield {
        type: 'content_block_start',
        index: 0,
        content_block: { type: 'text', text: '' },
      };
      yield {
        type: 'content_block_delta',
        index: 0,
        delta: { type: 'text_delta', text: 'Product details...' },
      };
      yield { type: 'message_delta', usage: { output_tokens: 12 } };
    })();

    mockStream
      .mockReturnValueOnce(firstEvents)
      .mockReturnValueOnce(secondEvents);

    const mockExecuteTool = jest
      .fn<Promise<unknown>, [string, Record<string, unknown>]>()
      .mockResolvedValue({ id: 1, name: 'Test' });

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

    const firstCallArgs = mockStream.mock.calls[0]?.[0] as {
      system?: string;
      messages?: Array<{ role: string; content: unknown }>;
    };
    expect(firstCallArgs?.system).toBe('system prompt');
    expect(firstCallArgs?.messages?.[0]).toEqual({
      role: 'user',
      content: 'You are a helpful assistant.',
    });

    expect(events.find((e) => e.type === 'tool_call')).toMatchObject({
      toolCallId: 'tu_123',
      toolName: 'get_product_deep',
    });
    expect(mockExecuteTool).toHaveBeenCalledWith('get_product_deep', { id: 1 });

    const doneEvent = events.find((e) => e.type === 'done');
    expect(doneEvent).toBeDefined();
  });

  it('yields error event on SDK failure', async () => {
    mockStream.mockImplementation(() => {
      throw new Error('Anthropic API error');
    });

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
      message: 'Anthropic API error',
    });
  });
});
