/* eslint-disable @typescript-eslint/unbound-method */
import { Test, TestingModule } from '@nestjs/testing';
import { McpService } from '../mcp.service';
import { ProviderRegistry } from '../../ai-providers/provider-registry';
import { InventoryClient } from '../inventory.client';
import { LlmEvent } from '../../common/interfaces/llm-provider.interface';
import { IMcpTool } from '../../common/interfaces/mcp-tool.interface';
import { Response } from 'express';
import { z } from 'zod';

// eslint-disable-next-line @typescript-eslint/require-await
async function* createMockStream(events: LlmEvent[]): AsyncIterable<LlmEvent> {
  for (const event of events) {
    yield event;
  }
}

function createMockTool(
  name: string,
  result: unknown,
  schema: z.ZodSchema = z.object({}),
): IMcpTool {
  return {
    name,
    definition: {
      name,
      description: `Mock tool ${name}`,
      inputSchema: { type: 'object', properties: {}, required: [] },
    },
    schema,
    execute: jest.fn().mockResolvedValue(result),
  };
}

jest.mock('../../common/logging/logger', () => ({
  mcpLogger: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
  },
}));

describe('McpService', () => {
  let service: McpService;
  let mockProviderRegistry: { getProvider: jest.Mock };
  let mockInventoryClient: Partial<InventoryClient>;
  let mockToolRegistry: Map<string, IMcpTool>;
  let mockRes: Partial<Response>;
  let writtenChunks: string[];

  beforeEach(async () => {
    writtenChunks = [];

    mockProviderRegistry = {
      getProvider: jest.fn(),
    };

    mockInventoryClient = {
      get: jest.fn(),
      post: jest.fn(),
    };

    mockToolRegistry = new Map<string, IMcpTool>();
    mockToolRegistry.set(
      'get_product_deep',
      createMockTool(
        'get_product_deep',
        { id: 1, name: 'Test Product' },
        z.object({ id: z.number() }),
      ),
    );

    mockRes = {
      write: jest.fn((chunk: string) => {
        writtenChunks.push(chunk);
        return true;
      }),
      end: jest.fn(),
      setHeader: jest.fn(),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        McpService,
        { provide: ProviderRegistry, useValue: mockProviderRegistry },
        { provide: InventoryClient, useValue: mockInventoryClient },
        { provide: 'TOOL_REGISTRY', useValue: mockToolRegistry },
      ],
    }).compile();

    service = module.get<McpService>(McpService);
  });

  const defaultBody = {
    systemPrompt: 'You are a helpful assistant',
    compressedContext: 'test context',
    apiKey: 'test-key',
    provider: 'OPENAI',
    userName: 'testuser',
    userLanguage: 'es',
    model: 'gpt-4o',
  };

  describe('streamCompletion - simple text response', () => {
    it('should emit token and done SSE events', async () => {
      const events: LlmEvent[] = [
        { type: 'text', content: 'Hello' },
        { type: 'text', content: ' world' },
        {
          type: 'done',
          fullText: 'Hello world',
          inputTokens: 10,
          outputTokens: 5,
        },
      ];

      const mockProvider = {
        streamCompletion: jest.fn().mockReturnValue(createMockStream(events)),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(
        defaultBody,
        'jwt-token',
        mockRes as Response,
      );

      expect(mockRes.write).toHaveBeenCalledTimes(3);
      expect(writtenChunks[0]).toContain('event: token');
      expect(writtenChunks[0]).toContain('"Hello"');
      expect(writtenChunks[1]).toContain('event: token');
      expect(writtenChunks[2]).toContain('event: done');
      expect(writtenChunks[2]).toContain('"fullResponse"');
      expect(writtenChunks[2]).toContain('"inputTokens":10');
      const endMock = mockRes.end as jest.Mock;
      expect(endMock).toHaveBeenCalledTimes(1);
    });
  });

  describe('streamCompletion - with tool calls', () => {
    it('should emit tool_called and tool_result SSE events', async () => {
      const events: LlmEvent[] = [
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
        { type: 'text', content: 'Here is the product' },
        {
          type: 'done',
          fullText: 'Here is the product',
          inputTokens: 20,
          outputTokens: 10,
        },
      ];

      const mockProvider = {
        streamCompletion: jest.fn().mockReturnValue(createMockStream(events)),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(defaultBody, 'jwt', mockRes as Response);

      expect(mockRes.write).toHaveBeenCalledTimes(4);
      expect(writtenChunks[0]).toContain('event: tool_called');
      expect(writtenChunks[1]).toContain('event: tool_result');
      expect(writtenChunks[2]).toContain('event: token');
      expect(writtenChunks[3]).toContain('event: done');
    });
  });

  describe('streamCompletion - error handling', () => {
    it('should propagate error when provider throws before stream setup', async () => {
      mockProviderRegistry.getProvider.mockImplementation(() => {
        throw new Error('Provider crashed');
      });

      await expect(
        service.streamCompletion(defaultBody, 'jwt', mockRes as Response),
      ).rejects.toThrow('Provider crashed');
    });

    it('should propagate error for unknown provider', async () => {
      mockProviderRegistry.getProvider.mockImplementation(() => {
        throw new Error('Provider "UNKNOWN" is not registered');
      });

      const body = { ...defaultBody, provider: 'UNKNOWN' };
      await expect(
        service.streamCompletion(body, 'jwt', mockRes as Response),
      ).rejects.toThrow('not registered');
    });

    it('should break on error event from stream', async () => {
      const events: LlmEvent[] = [
        { type: 'text', content: 'Hello' },
        { type: 'error', message: 'Something went wrong' },
        { type: 'text', content: 'This should NOT appear' },
      ];

      const mockProvider = {
        streamCompletion: jest.fn().mockReturnValue(createMockStream(events)),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(defaultBody, 'jwt', mockRes as Response);

      expect(mockRes.write).toHaveBeenCalledTimes(2);
      expect(writtenChunks[0]).toContain('event: token');
      expect(writtenChunks[1]).toContain('event: error');
    });
  });

  describe('streamCompletion - tool definitions', () => {
    it('should pass all tool definitions to the provider', async () => {
      mockToolRegistry.set(
        'unified_search',
        createMockTool('unified_search', { results: [] }),
      );

      const mockProvider = {
        streamCompletion: jest
          .fn()
          .mockReturnValue(
            createMockStream([
              { type: 'done', fullText: '', inputTokens: 0, outputTokens: 0 },
            ]),
          ),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(defaultBody, 'jwt', mockRes as Response);

      const streamCalls = mockProvider.streamCompletion.mock.calls as Array<
        [Record<string, unknown>, Array<{ name: string }>]
      >;
      const toolsArg = streamCalls[0]?.[1] ?? [];
      expect(toolsArg).toHaveLength(2);
      expect(toolsArg.map((t) => t.name)).toEqual(
        expect.arrayContaining(['get_product_deep', 'unified_search']),
      );
    });
  });

  describe('streamCompletion - provider params', () => {
    it('should pass correct LlmCompletionParams to provider', async () => {
      const mockProvider = {
        streamCompletion: jest
          .fn()
          .mockReturnValue(
            createMockStream([
              { type: 'done', fullText: '', inputTokens: 0, outputTokens: 0 },
            ]),
          ),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      const body = { ...defaultBody, temperature: 0.7 };
      await service.streamCompletion(body, 'jwt', mockRes as Response);

      expect(mockProviderRegistry.getProvider).toHaveBeenCalledWith(
        'OPENAI',
        'test-key',
      );

      const streamCalls = mockProvider.streamCompletion.mock.calls as Array<
        [Record<string, unknown>, Array<Record<string, unknown>>]
      >;
      const params = streamCalls[0]?.[0] ?? {};
      expect(params).toEqual({
        systemPrompt: 'You are a helpful assistant',
        compressedContext: 'test context',
        model: 'gpt-4o',
        temperature: 0.7,
      });
    });
  });

  describe('executeTool (via streamCompletion callback)', () => {
    it('should throw when tool is not in registry', async () => {
      let capturedExecuteTool:
        | ((name: string, args: unknown) => Promise<unknown>)
        | undefined;

      const mockProvider = {
        streamCompletion: jest
          .fn()
          .mockImplementation(
            (
              _params: unknown,
              _tools: unknown,
              executeTool: (name: string, args: unknown) => Promise<unknown>,
            ) => {
              capturedExecuteTool = executeTool;
              return createMockStream([
                { type: 'done', fullText: '', inputTokens: 0, outputTokens: 0 },
              ]);
            },
          ),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(defaultBody, 'jwt', mockRes as Response);

      await expect(
        capturedExecuteTool?.('nonexistent_tool', {}),
      ).rejects.toThrow('Tool "nonexistent_tool" not found in registry');
    });

    it('should validate args with Zod schema before executing', async () => {
      let capturedExecuteTool:
        | ((name: string, args: unknown) => Promise<unknown>)
        | undefined;

      const mockProvider = {
        streamCompletion: jest
          .fn()
          .mockImplementation(
            (
              _params: unknown,
              _tools: unknown,
              executeTool: (name: string, args: unknown) => Promise<unknown>,
            ) => {
              capturedExecuteTool = executeTool;
              return createMockStream([
                { type: 'done', fullText: '', inputTokens: 0, outputTokens: 0 },
              ]);
            },
          ),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(defaultBody, 'jwt', mockRes as Response);

      await expect(
        capturedExecuteTool?.('get_product_deep', { id: 'not-a-number' }),
      ).rejects.toThrow();
    });

    it('should call tool.execute with validated args, inventoryClient, and jwt', async () => {
      let capturedExecuteTool:
        | ((name: string, args: unknown) => Promise<unknown>)
        | undefined;

      const mockProvider = {
        streamCompletion: jest
          .fn()
          .mockImplementation(
            (
              _params: unknown,
              _tools: unknown,
              executeTool: (name: string, args: unknown) => Promise<unknown>,
            ) => {
              capturedExecuteTool = executeTool;
              return createMockStream([
                { type: 'done', fullText: '', inputTokens: 0, outputTokens: 0 },
              ]);
            },
          ),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(
        defaultBody,
        'my-jwt',
        mockRes as Response,
      );

      const result = await capturedExecuteTool?.('get_product_deep', {
        id: 42,
      });

      const tool = mockToolRegistry.get('get_product_deep');
      const executeMock = tool?.execute as jest.Mock;
      expect(executeMock).toHaveBeenCalledWith(
        { id: 42 },
        mockInventoryClient,
        'my-jwt',
      );
      expect(result).toEqual({ id: 1, name: 'Test Product' });
    });
  });

  describe('emitSse (via streamCompletion)', () => {
    it('should format done event with fullResponse, inputTokens, outputTokens', async () => {
      const mockProvider = {
        streamCompletion: jest.fn().mockReturnValue(
          createMockStream([
            {
              type: 'done',
              fullText: 'Complete response',
              inputTokens: 100,
              outputTokens: 50,
            },
          ]),
        ),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(defaultBody, 'jwt', mockRes as Response);

      const doneChunk = writtenChunks.find((c) => c.includes('event: done'));
      expect(doneChunk).toBeDefined();
      const dataLine = doneChunk
        ?.split('\n')
        .find((l) => l.startsWith('data: '));
      const parsed = JSON.parse((dataLine ?? '').replace('data: ', '')) as {
        fullResponse: string;
        inputTokens: number;
        outputTokens: number;
      };
      expect(parsed).toEqual({
        fullResponse: 'Complete response',
        inputTokens: 100,
        outputTokens: 50,
      });
    });

    it('should always call res.end() even on error', async () => {
      const mockProvider = {
        streamCompletion: jest.fn().mockImplementation(() => {
          throw new Error('Fatal');
        }),
      };
      mockProviderRegistry.getProvider.mockReturnValue(mockProvider);

      await service.streamCompletion(defaultBody, 'jwt', mockRes as Response);

      const endMock = mockRes.end as jest.Mock;
      expect(endMock).toHaveBeenCalledTimes(1);
    });
  });
});
