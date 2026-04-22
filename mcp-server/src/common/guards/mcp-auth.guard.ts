import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  Logger,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Request } from 'express';

@Injectable()
export class McpAuthGuard implements CanActivate {
  private readonly logger = new Logger(McpAuthGuard.name);

  constructor(private readonly configService: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const raw = request.headers['x-service-key'];
    const incomingKey = Array.isArray(raw) ? raw[0] : raw;
    const expectedKey = this.configService.get<string>('SERVICE_KEY');

    if (!incomingKey || incomingKey !== expectedKey) {
      this.logger.warn(
        `Auth failed: Incoming key: ${incomingKey || 'MISSING'}. Expected key matches: ${expectedKey === incomingKey}`,
      );
      throw new ForbiddenException('Invalid or missing X-Service-Key header');
    }

    return true;
  }
}
