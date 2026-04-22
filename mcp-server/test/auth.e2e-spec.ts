import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { createTestApp } from './helpers/create-test-app';
import { createMockProviderRegistry } from './helpers/mock-providers';

process.env.SERVICE_KEY = 'test-key-auth-e2e';

describe('Auth (e2e)', () => {
  let app: INestApplication;

  const validBody = {
    systemPrompt: 'Test system prompt',
    compressedContext: 'contexto de prueba',
    apiKey: 'provider-api-key',
    provider: 'OPENAI',
    userName: 'tester',
    userLanguage: 'es',
    model: 'gpt-4o',
  };

  beforeAll(async () => {
    const providerRegistryMock = createMockProviderRegistry([
      { type: 'text', content: 'ok' },
      {
        type: 'done',
        fullText: 'ok',
        inputTokens: 1,
        outputTokens: 1,
      },
    ]);

    app = await createTestApp({ providerRegistryMock });
  });

  afterAll(async () => {
    await app.close();
  });

  it('POST /api/completion without X-Service-Key returns 403', async () => {
    await request(app.getHttpServer())
      .post('/api/completion')
      .send(validBody)
      .expect(403);
  });

  it('POST /api/completion with wrong X-Service-Key returns 403', async () => {
    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', 'wrong-key')
      .send(validBody)
      .expect(403);
  });

  it('POST /api/completion with valid X-Service-Key returns 200', async () => {
    const response = await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(validBody)
      .expect(200);

    expect(response.text).toContain('event: done');
  });
});
