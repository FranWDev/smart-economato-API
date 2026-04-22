import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import { timeout } from 'rxjs/operators';

@Controller('health')
export class HealthController {
  private readonly backendUrl: string;

  constructor(
    private readonly configService: ConfigService,
    private readonly httpService: HttpService,
  ) {
    this.backendUrl = this.configService.get<string>('BACKEND_BASE_URL') || '';
  }

  /**
   * Liveness probe — indica que el proceso está vivo.
   * Usado por Docker HEALTHCHECK o K8s livenessProbe.
   */
  @Get()
  liveness() {
    return {
      status: 'ok',
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Readiness probe — verifica que el servicio puede atender requests.
   * Comprueba conectividad con el backend de inventario.
   * Usado por K8s readinessProbe o load balancers.
   */
  @Get('ready')
  async readiness() {
    try {
      const healthUrl = new URL('/actuator/health', this.backendUrl).toString();

      await firstValueFrom(this.httpService.get(healthUrl).pipe(timeout(5000)));

      return {
        status: 'ready',
        backend: 'reachable',
        timestamp: new Date().toISOString(),
      };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Unknown error';

      throw new ServiceUnavailableException({
        status: 'not_ready',
        backend: 'unreachable',
        error: message,
        timestamp: new Date().toISOString(),
      });
    }
  }
}
