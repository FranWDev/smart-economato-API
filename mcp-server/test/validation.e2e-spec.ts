import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { createTestApp } from './helpers/create-test-app';
import { createMockProviderRegistry } from './helpers/mock-providers';

process.env.SERVICE_KEY = 'test-key-validation-e2e';

describe('Completion Validation (e2e)', () => {
  let app: INestApplication;

  const validBody = {
    systemPrompt: 'Test system prompt',
    compressedContext: 'contexto valido',
    apiKey: 'provider-api-key',
    provider: 'OPENAI',
    userName: 'tester',
    userLanguage: 'es',
    model: 'gpt-4o',
    temperature: 0.2,
  };

  const omitField = <T extends Record<string, unknown>>(
    obj: T,
    key: keyof T,
  ): Partial<T> => {
    const copy = { ...obj };
    delete copy[key];
    return copy;
  };

  beforeAll(async () => {
    const providerRegistryMock = createMockProviderRegistry([
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

  it('POST /api/completion without compressedContext returns 400', async () => {
    const body = omitField(validBody, 'compressedContext');

    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(body)
      .expect(400);
  });

  it('POST /api/completion without provider returns 400', async () => {
    const body = omitField(validBody, 'provider');

    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(body)
      .expect(400);
  });

  it('POST /api/completion without apiKey returns 400', async () => {
    const body = omitField(validBody, 'apiKey');

    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(body)
      .expect(400);
  });

  it('POST /api/completion without model returns 400', async () => {
    const body = omitField(validBody, 'model');

    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(body)
      .expect(400);
  });

  it('POST /api/completion without userName returns 400', async () => {
    const body = omitField(validBody, 'userName');

    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(body)
      .expect(400);
  });

  it('POST /api/completion without userLanguage returns 400', async () => {
    const body = omitField(validBody, 'userLanguage');

    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(body)
      .expect(400);
  });

  it('POST /api/completion with extra non-whitelisted field returns 400', async () => {
    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send({ ...validBody, extraField: 'not-allowed' })
      .expect(400);
  });

  it('POST /api/completion with full valid body returns 200', async () => {
    await request(app.getHttpServer())
      .post('/api/completion')
      .set('X-Service-Key', process.env.SERVICE_KEY as string)
      .send(validBody)
      .expect(200);
  });
});
