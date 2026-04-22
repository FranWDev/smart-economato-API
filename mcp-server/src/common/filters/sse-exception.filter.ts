import {
  ExceptionFilter,
  Catch,
  ArgumentsHost,
  HttpException,
  Logger,
} from '@nestjs/common';
import { Response } from 'express';
import { mcpLogger } from '../logging/logger';

@Catch()
export class SseExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(SseExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const res = ctx.getResponse<Response>();

    const { message, stack } = this.getExceptionDetails(exception);
    const status =
      exception instanceof HttpException ? exception.getStatus() : 500;

    this.logger.error(`Unhandled exception: ${message}`, stack);
    mcpLogger.error(
      `[SseExceptionFilter] Unhandled exception: ${message}`,
      exception,
    );

    if (res.headersSent) {
      try {
        res.write(
          `event: error\ndata: ${JSON.stringify({ message, status })}\n\n`,
        );
      } catch {
        this.logger.error(
          'Failed to write SSE error event - response already closed',
        );
      } finally {
        res.end();
      }
      return;
    }

    if (exception instanceof HttpException) {
      const response = exception.getResponse();

      if (typeof response === 'object' && response !== null) {
        res.status(status).json({
          ...response,
          statusCode: status,
          timestamp:
            'timestamp' in response && typeof response.timestamp === 'string'
              ? response.timestamp
              : new Date().toISOString(),
        });
        return;
      }
    }

    res.status(status).json({
      statusCode: status,
      message,
      timestamp: new Date().toISOString(),
    });
  }

  private getExceptionDetails(exception: unknown) {
    if (exception instanceof Error) {
      return {
        message: exception.message,
        stack: exception.stack,
      };
    }

    if (
      typeof exception === 'object' &&
      exception !== null &&
      'message' in exception
    ) {
      const message = exception.message;

      if (typeof message === 'string') {
        return {
          message,
        };
      }
    }

    return {
      message: 'Internal server error',
    };
  }
}
