import {
  ArgumentsHost,
  BadRequestException,
  ForbiddenException,
  HttpStatus,
} from '@nestjs/common';
import { Response } from 'express';
import { SseExceptionFilter } from '../sse-exception.filter';

jest.mock('../../logging/logger', () => ({
  mcpLogger: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
  },
}));

describe('SseExceptionFilter', () => {
  let filter: SseExceptionFilter;
  let mockRes: Partial<Response>;
  let mockHost: ArgumentsHost;
  let writtenData: string[];

  beforeEach(() => {
    filter = new SseExceptionFilter();
    writtenData = [];

    mockRes = {
      headersSent: false,
      write: jest.fn((data: string) => {
        writtenData.push(data);
        return true;
      }),
      end: jest.fn(),
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    };

    mockHost = {
      switchToHttp: () => ({
        getResponse: () => mockRes as Response,
        getRequest: () => ({}),
        getNext: () => jest.fn(),
      }),
      getArgs: () => [],
      getArgByIndex: () => undefined,
      switchToRpc: () => ({}) as never,
      switchToWs: () => ({}) as never,
      getType: () => 'http',
    } as unknown as ArgumentsHost;
  });

  describe('when headers NOT sent (normal HTTP response)', () => {
    beforeEach(() => {
      mockRes.headersSent = false;
    });

    it('should return JSON error with status 500 for generic Error', () => {
      filter.catch(new Error('Something broke'), mockHost);

      expect(mockRes.status).toHaveBeenCalledWith(500);
      expect(mockRes.json).toHaveBeenCalledWith(
        expect.objectContaining({
          statusCode: 500,
          message: 'Something broke',
        }),
      );
      const jsonMock = mockRes.json as jest.Mock;
      const jsonCalls = jsonMock.mock.calls as Array<[Record<string, unknown>]>;
      const payload = (jsonCalls[0]?.[0] ?? {}) as {
        timestamp?: string;
      };
      expect(typeof payload.timestamp).toBe('string');
      expect(mockRes.write).not.toHaveBeenCalled();
      expect(mockRes.end).not.toHaveBeenCalled();
    });

    it('should use HttpException status code when available', () => {
      filter.catch(new ForbiddenException('Access denied'), mockHost);

      expect(mockRes.status).toHaveBeenCalledWith(HttpStatus.FORBIDDEN);
      expect(mockRes.json).toHaveBeenCalledWith(
        expect.objectContaining({
          statusCode: 403,
          message: 'Access denied',
        }),
      );
    });

    it('should use BadRequestException status code (400)', () => {
      filter.catch(new BadRequestException('Invalid input'), mockHost);

      expect(mockRes.status).toHaveBeenCalledWith(HttpStatus.BAD_REQUEST);
      expect(mockRes.json).toHaveBeenCalledWith(
        expect.objectContaining({
          statusCode: 400,
          message: 'Invalid input',
        }),
      );
    });

    it('should handle non-Error exceptions with default message', () => {
      filter.catch('string exception', mockHost);

      expect(mockRes.status).toHaveBeenCalledWith(500);
      expect(mockRes.json).toHaveBeenCalledWith(
        expect.objectContaining({
          statusCode: 500,
          message: 'Internal server error',
        }),
      );
    });

    it('should handle null exception with default message', () => {
      filter.catch(null, mockHost);

      expect(mockRes.status).toHaveBeenCalledWith(500);
      expect(mockRes.json).toHaveBeenCalledWith(
        expect.objectContaining({
          message: 'Internal server error',
        }),
      );
    });

    it('should handle object with message property', () => {
      filter.catch({ message: 'Custom object error' }, mockHost);

      expect(mockRes.status).toHaveBeenCalledWith(500);
      expect(mockRes.json).toHaveBeenCalledWith(
        expect.objectContaining({
          message: 'Custom object error',
        }),
      );
    });
  });

  describe('when headers already sent (SSE mode)', () => {
    beforeEach(() => {
      mockRes.headersSent = true;
    });

    it('should write SSE error event and call res.end()', () => {
      filter.catch(new Error('Stream failed'), mockHost);

      expect(mockRes.write).toHaveBeenCalledTimes(1);

      const written = writtenData[0] ?? '';
      expect(written).toContain('event: error');
      expect(written).toContain('"message":"Stream failed"');
      expect(written).toContain('"status":500');

      expect(mockRes.end).toHaveBeenCalledTimes(1);
      expect(mockRes.status).not.toHaveBeenCalled();
      expect(mockRes.json).not.toHaveBeenCalled();
    });

    it('should use HttpException status in SSE error event', () => {
      filter.catch(new ForbiddenException('Forbidden'), mockHost);

      const written = writtenData[0] ?? '';
      expect(written).toContain('"status":403');
      expect(written).toContain('"message":"Forbidden"');
      expect(mockRes.end).toHaveBeenCalledTimes(1);
    });

    it('should handle res.write failure gracefully', () => {
      (mockRes.write as jest.Mock).mockImplementation(() => {
        throw new Error('Write failed - connection reset');
      });

      expect(() => {
        filter.catch(new Error('Original error'), mockHost);
      }).not.toThrow();

      expect(mockRes.end).toHaveBeenCalledTimes(1);
    });

    it('should handle non-Error exception in SSE mode', () => {
      filter.catch(42, mockHost);

      const written = writtenData[0] ?? '';
      expect(written).toContain('event: error');
      expect(written).toContain('"message":"Internal server error"');
      expect(written).toContain('"status":500');
      expect(mockRes.end).toHaveBeenCalledTimes(1);
    });
  });
});
