import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { createTestApp } from './helpers/create-test-app';
import {
  createMockProviderRegistry,
  parseSseResponse,
} from './helpers/mock-providers';

process.env.SERVICE_KEY = 'test-key-completion-e2e';

describe('Completion SSE (e2e)', () => {
  let app: INestApplication;
  let providerRegistryMock: ReturnType<typeof createMockProviderRegistry>;

  const validBody = {
    systemPrompt: 'Test system prompt',
    compressedContext: 'contexto de prueba para completions',
    apiKey: 'provider-api-key',
    provider: 'OPENAI',
    userName: 'tester',
    userLanguage: 'es',
    model: 'gpt-4o',
  };

  beforeAll(async () => {
    providerRegistryMock = createMockProviderRegistry([
      { type: 'text', content: 'Hello' },
      { type: 'text', content: ' world' },
      {
        type: 'done',
        fullText: 'Hello world',
        inputTokens: 10,
        outputTokens: 5,
      },
    ]);

    app = await createTestApp({ providerRegistryMock });
  });

  afterAll(async () => {
    await app.close();
  });

  it('returns SSE headers and emits token/token/done events', async () => {
    const response = await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(validBody)
      .expect(200);

    expect(response.headers['content-type']).toContain('text/event-stream');
    expect(response.headers['cache-control']).toContain('no-cache');

    const events = parseSseResponse(response.text);
    expect(events).toHaveLength(3);

    expect(events[0]).toEqual({ event: 'token', data: 'Hello' });
    expect(events[1]).toEqual({ event: 'token', data: ' world' });
    expect(events[2]).toEqual({
      event: 'done',
      data: {
        fullResponse: 'Hello world',
        inputTokens: 10,
        outputTokens: 5,
      },
    });
  });

  it('passes provider and apiKey from body to ProviderRegistry.getProvider', async () => {
    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(validBody)
      .expect(200);

    expect(providerRegistryMock.getProvider).toHaveBeenCalledWith(
      validBody.provider,
      validBody.apiKey,
    );
  });
});
