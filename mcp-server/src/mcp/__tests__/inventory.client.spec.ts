import { Test, TestingModule } from '@nestjs/testing';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { InventoryClient } from '../inventory.client';
import { of, throwError } from 'rxjs';
import { AxiosHeaders, AxiosResponse } from 'axios';

jest.mock('../../common/logging/logger', () => ({
  mcpLogger: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
  },
}));

describe('InventoryClient', () => {
  let client: InventoryClient;
  let httpService: { request: jest.Mock };

  const firstRequestConfig = (): Record<string, unknown> => {
    const calls = httpService.request.mock.calls as Array<
      [Record<string, unknown>]
    >;
    return calls[0]?.[0] ?? {};
  };

  const mockConfigValues: Record<string, string | number> = {
    BACKEND_BASE_URL: 'http://localhost:8080',
    SERVICE_KEY: 'test-service-key',
    INVENTORY_TIMEOUT_MS: 5000,
    INVENTORY_MAX_RETRIES: 3,
  };

  const makeAxiosResponse = <T>(data: T, status = 200): AxiosResponse<T> => ({
    data,
    status,
    statusText: 'OK',
    headers: {},
    config: { headers: new AxiosHeaders() },
  });

  beforeEach(async () => {
    httpService = { request: jest.fn() };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        InventoryClient,
        {
          provide: HttpService,
          useValue: httpService,
        },
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn((key: string) => mockConfigValues[key]),
          },
        },
      ],
    }).compile();

    client = module.get<InventoryClient>(InventoryClient);

    jest.spyOn(client as never, 'sleep').mockResolvedValue(undefined);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('returns data on successful GET', async () => {
    const mockData = { id: 1, name: 'Product' };
    httpService.request.mockReturnValue(of(makeAxiosResponse(mockData)));

    const result = await client.get('/api/test', 'Bearer token');

    expect(result).toEqual(mockData);
    expect(httpService.request).toHaveBeenCalledTimes(1);
  });

  it('returns data on successful POST', async () => {
    const mockData = { success: true };
    httpService.request.mockReturnValue(of(makeAxiosResponse(mockData)));

    const result = await client.post(
      '/api/test',
      { ids: [1, 2] },
      'Bearer token',
    );

    expect(result).toEqual(mockData);
    expect(httpService.request).toHaveBeenCalledTimes(1);
    const callConfig = firstRequestConfig() as {
      data?: Record<string, unknown>;
    };
    expect(callConfig.data).toEqual({ ids: [1, 2] });
  });

  it('includes X-Service-Key header', async () => {
    httpService.request.mockReturnValue(of(makeAxiosResponse({})));

    await client.get('/api/test');

    const callConfig = firstRequestConfig() as {
      headers: Record<string, string | undefined>;
    };
    expect(callConfig.headers['X-Service-Key']).toBe('test-service-key');
  });

  it('includes Authorization header when JWT provided', async () => {
    httpService.request.mockReturnValue(of(makeAxiosResponse({})));

    await client.get('/api/test', 'Bearer my-jwt');

    const callConfig = firstRequestConfig() as {
      headers: Record<string, string | undefined>;
    };
    expect(callConfig.headers.Authorization).toBe('Bearer my-jwt');
  });

  it('does not include Authorization header when JWT not provided', async () => {
    httpService.request.mockReturnValue(of(makeAxiosResponse({})));

    await client.get('/api/test');

    const callConfig = firstRequestConfig() as {
      headers: Record<string, string | undefined>;
    };
    expect(callConfig.headers.Authorization).toBeUndefined();
  });

  it('includes query params when provided', async () => {
    httpService.request.mockReturnValue(of(makeAxiosResponse({})));

    await client.get('/api/test', 'jwt', { days: 7 });

    const callConfig = firstRequestConfig() as {
      params?: Record<string, unknown>;
    };
    expect(callConfig.params).toEqual({ days: 7 });
  });

  it('retries on 500 and succeeds on second attempt', async () => {
    const error500 = {
      response: { status: 500, data: { message: 'Internal error' } },
    };
    const successResponse = makeAxiosResponse({ ok: true });

    httpService.request
      .mockReturnValueOnce(throwError(() => error500))
      .mockReturnValueOnce(of(successResponse));

    const result = await client.get('/api/test');

    expect(result).toEqual({ ok: true });
    expect(httpService.request).toHaveBeenCalledTimes(2);
  });

  it('retries on network error (ECONNREFUSED)', async () => {
    const networkError = {
      code: 'ECONNREFUSED',
      message: 'Connection refused',
    };
    const successResponse = makeAxiosResponse({ ok: true });

    httpService.request
      .mockReturnValueOnce(throwError(() => networkError))
      .mockReturnValueOnce(of(successResponse));

    const result = await client.get('/api/test');

    expect(result).toEqual({ ok: true });
    expect(httpService.request).toHaveBeenCalledTimes(2);
  });

  it('retries on timeout error', async () => {
    const timeoutError = { name: 'TimeoutError', message: 'Timeout' };
    const successResponse = makeAxiosResponse({ ok: true });

    httpService.request
      .mockReturnValueOnce(throwError(() => timeoutError))
      .mockReturnValueOnce(of(successResponse));

    const result = await client.get('/api/test');

    expect(result).toEqual({ ok: true });
    expect(httpService.request).toHaveBeenCalledTimes(2);
  });

  it('exhausts retries and throws on persistent 500', async () => {
    const error500 = {
      response: { status: 500, data: { message: 'Server down' } },
    };

    httpService.request.mockReturnValue(throwError(() => error500));

    await expect(client.get('/api/test')).rejects.toThrow(
      'Inventory API Error (500): Server down',
    );
    expect(httpService.request).toHaveBeenCalledTimes(3);
  });

  it('throws timeout-specific error message', async () => {
    const timeoutError = { name: 'TimeoutError', message: 'Timeout' };

    httpService.request.mockReturnValue(throwError(() => timeoutError));

    await expect(client.get('/api/test')).rejects.toThrow(
      /Inventory API Timeout after 5000ms/,
    );
  });

  it('uses exponential backoff between retries', async () => {
    const sleepSpy = jest.spyOn(client as never, 'sleep');
    const error500 = { response: { status: 500, data: { message: 'fail' } } };

    httpService.request.mockReturnValue(throwError(() => error500));

    await expect(client.get('/api/test')).rejects.toThrow();

    expect(sleepSpy).toHaveBeenCalledTimes(2);
    expect(sleepSpy).toHaveBeenNthCalledWith(1, 1000);
    expect(sleepSpy).toHaveBeenNthCalledWith(2, 2000);
  });

  it('does not retry on 400 Bad Request', async () => {
    const error400 = {
      response: { status: 400, data: { message: 'Bad request' } },
    };

    httpService.request.mockReturnValue(throwError(() => error400));

    await expect(client.get('/api/test')).rejects.toThrow(
      'Inventory API Error (400): Bad request',
    );
    expect(httpService.request).toHaveBeenCalledTimes(1);
  });

  it('does not retry on 401 Unauthorized', async () => {
    const error401 = {
      response: { status: 401, data: { message: 'Unauthorized' } },
    };

    httpService.request.mockReturnValue(throwError(() => error401));

    await expect(client.get('/api/test')).rejects.toThrow(
      'Inventory API Error (401): Unauthorized',
    );
    expect(httpService.request).toHaveBeenCalledTimes(1);
  });

  it('does not retry on 404 Not Found', async () => {
    const error404 = {
      response: { status: 404, data: { message: 'Not found' } },
    };

    httpService.request.mockReturnValue(throwError(() => error404));

    await expect(client.get('/api/test')).rejects.toThrow(
      'Inventory API Error (404): Not found',
    );
    expect(httpService.request).toHaveBeenCalledTimes(1);
  });
});
