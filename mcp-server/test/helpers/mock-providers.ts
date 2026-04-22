import { ProviderRegistry } from '../../src/ai-providers/provider-registry';
import {
  ILlmProvider,
  LlmEvent,
} from '../../src/common/interfaces/llm-provider.interface';

async function* eventStream(events: LlmEvent[]): AsyncIterable<LlmEvent> {
  for (const event of events) {
    await Promise.resolve();
    yield event;
  }
}

export function createMockProviderRegistry(events: LlmEvent[]): {
  getProvider: jest.MockedFunction<ProviderRegistry['getProvider']>;
  mockProvider: jest.Mocked<ILlmProvider>;
} {
  const mockProvider: jest.Mocked<ILlmProvider> = {
    streamCompletion: jest.fn().mockImplementation(() => eventStream(events)),
  };

  const getProvider = jest
    .fn<ProviderRegistry['getProvider']>()
    .mockReturnValue(mockProvider);

  return {
    getProvider,
    mockProvider,
  };
}

export function parseSseResponse(
  text: string,
): Array<{ event: string; data: unknown }> {
  const rawEvents = text
    .split('\n\n')
    .map((chunk) => chunk.trim())
    .filter((chunk) => chunk.length > 0);

  return rawEvents.map((chunk) => {
    const lines = chunk.split('\n');
    const eventLine = lines.find((line) => line.startsWith('event:'));
    const dataLines = lines
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.replace(/^data:\s*/, ''));

    const event = eventLine?.replace(/^event:\s*/, '') ?? 'message';
    const dataText = dataLines.join('\n');

    try {
      return { event, data: JSON.parse(dataText) as unknown };
    } catch {
      return { event, data: dataText };
    }
  });
}
