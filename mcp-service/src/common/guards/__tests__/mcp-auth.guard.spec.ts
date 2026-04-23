import { ExecutionContext, ForbiddenException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { McpAuthGuard } from '../mcp-auth.guard';

describe('McpAuthGuard', () => {
  let guard: McpAuthGuard;
  let configService: { get: jest.Mock };

  const createMockContext = (
    headers: Record<string, string | string[] | undefined>,
  ): ExecutionContext => {
    return {
      switchToHttp: () => ({
        getRequest: () => ({ headers }),
      }),
    } as unknown as ExecutionContext;
  };

  beforeEach(() => {
    configService = {
      get: jest.fn().mockReturnValue('valid-service-key-32-chars-long!!'),
    };
    guard = new McpAuthGuard(configService as unknown as ConfigService);
  });

  it('allows request with valid X-Service-Key', () => {
    const context = createMockContext({
      'x-service-key': 'valid-service-key-32-chars-long!!',
    });

    expect(guard.canActivate(context)).toBe(true);
  });

  it('uses first value when X-Service-Key is an array', () => {
    const context = createMockContext({
      'x-service-key': ['valid-service-key-32-chars-long!!', 'other'],
    });

    expect(guard.canActivate(context)).toBe(true);
  });

  it('throws ForbiddenException when X-Service-Key is missing', () => {
    const context = createMockContext({});

    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('throws ForbiddenException when X-Service-Key is wrong', () => {
    const context = createMockContext({
      'x-service-key': 'wrong-key',
    });

    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('throws ForbiddenException when X-Service-Key is empty string', () => {
    const context = createMockContext({
      'x-service-key': '',
    });

    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });
});
