import {
  Injectable,
  NestInterceptor,
  ExecutionContext,
  CallHandler,
  Logger,
} from '@nestjs/common';
import { Request } from 'express';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { mcpLogger } from './logger';

@Injectable()
export class RequestInterceptor implements NestInterceptor {
  private readonly logger = new Logger('IncomingRequest');

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<Request>();
    const method = request.method;
    const url = request.url;
    const body = request.body as Record<string, unknown> | undefined;
    const user = typeof body?.userName === 'string' ? body.userName : 'unknown';
    const provider =
      typeof body?.provider === 'string' ? body.provider : 'none';
    const model = typeof body?.model === 'string' ? body.model : 'none';

    const startTime = Date.now();

    // Log inmediato de entrada
    const incomingMessage = `--> ${method} ${url} | User: ${user} | Provider: ${provider} | Model: ${model}`;
    this.logger.log(incomingMessage);
    mcpLogger.info(incomingMessage, { body });

    return next.handle().pipe(
      tap(() => {
        const duration = Date.now() - startTime;
        const completionMessage = `<-- ${method} ${url} | Completed in ${duration}ms`;

        this.logger.log(completionMessage);
        mcpLogger.info(completionMessage);
      }),
    );
  }
}
