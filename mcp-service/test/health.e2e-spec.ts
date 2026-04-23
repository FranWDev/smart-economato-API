import { INestApplication } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { of, throwError } from 'rxjs';
import request from 'supertest';
import { createTestApp } from './helpers/create-test-app';

describe('Health (e2e)', () => {
  beforeEach(() => {
    process.env.BACKEND_BASE_URL = 'http://inventory-backend.local';
  });

  it('GET /health returns 200 with status ok and timestamp', async () => {
    const app = await createTestApp();

    const response = await request(app.getHttpServer())
      .get('/health')
      .expect(200);
    const payload = response.body as { status: string; timestamp: string };

    expect(payload.status).toBe('ok');
    expect(typeof payload.timestamp).toBe('string');

    await app.close();
  });

  it('GET /health does not require X-Service-Key', async () => {
    const app = await createTestApp();

    await request(app.getHttpServer()).get('/health').expect(200);

    await app.close();
  });

  it('GET /health/ready returns 200 when backend is reachable', async () => {
    const httpServiceMock: Pick<HttpService, 'get' | 'request'> = {
      get: jest
        .fn()
        .mockReturnValue(of({ data: { status: 'UP' }, status: 200 })),
      request: jest.fn(),
    };

    const app: INestApplication = await createTestApp({ httpServiceMock });

    const response = await request(app.getHttpServer())
      .get('/health/ready')
      .expect(200);
    const payload = response.body as { status: string; backend: string };

    expect(payload.status).toBe('ready');
    expect(payload.backend).toBe('reachable');

    await app.close();
  });

  it('GET /health/ready returns 503 when backend is unreachable', async () => {
    const httpServiceMock: Pick<HttpService, 'get' | 'request'> = {
      get: jest
        .fn()
        .mockReturnValue(throwError(() => new Error('backend down'))),
      request: jest.fn(),
    };

    const app: INestApplication = await createTestApp({ httpServiceMock });

    const response = await request(app.getHttpServer())
      .get('/health/ready')
      .expect(503);
    const payload = response.body as { status: string; backend: string };

    expect(payload.status).toBe('not_ready');
    expect(payload.backend).toBe('unreachable');

    await app.close();
  });
});
