import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { TimeoutError, firstValueFrom } from 'rxjs';
import { timeout as rxTimeout } from 'rxjs/operators';
import { AxiosRequestConfig } from 'axios';
import { mcpLogger } from '../common/logging/logger';

type InventoryErrorInfo = {
  status: number;
  message: string;
  detail: unknown;
  isTimeout: boolean;
  isNetworkError: boolean;
};

@Injectable()
export class InventoryClient {
  private readonly logger = new Logger(InventoryClient.name);
  private readonly baseUrl: string;
  private readonly serviceKey: string;
  private readonly timeoutMs: number;
  private readonly maxRetries: number;

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
  ) {
    this.baseUrl = this.configService.get<string>('BACKEND_BASE_URL') || '';
    this.serviceKey = this.configService.get<string>('SERVICE_KEY') || '';
    this.timeoutMs =
      this.configService.get<number>('INVENTORY_TIMEOUT_MS') || 10000;
    this.maxRetries =
      this.configService.get<number>('INVENTORY_MAX_RETRIES') || 3;

    this.logger.log(
      `[InventoryClient] Config loaded: timeoutMs=${this.timeoutMs}, maxRetries=${this.maxRetries}`,
    );
  }

  /**
   * Realiza una petición al backend de inventario con retry automático
   * y timeout configurable.
   */
  async request<T>(
    method: 'GET' | 'POST',
    path: string,
    data?: unknown,
    userJwt?: string,
    params?: Record<string, unknown>,
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;

    const config: AxiosRequestConfig = {
      method,
      url,
      headers: {
        'X-Service-Key': this.serviceKey,
        ...(userJwt ? { Authorization: userJwt } : {}),
      },
      ...(data ? { data } : {}),
      ...(params ? { params } : {}),
    };

    mcpLogger.info(`[InventoryClient] Outgoing Request: ${method} ${url}`, {
      params,
      data,
    });

    return this.requestWithRetry<T>(config, url);
  }

  /**
   * Ejecuta la petición HTTP con retry y backoff exponencial.
   * Solo reintenta en errores transitorios: 5xx, timeouts, errores de red.
   */
  private async requestWithRetry<T>(
    config: AxiosRequestConfig,
    url: string,
  ): Promise<T> {
    for (let attempt = 1; attempt <= this.maxRetries; attempt++) {
      try {
        const response = await firstValueFrom(
          this.httpService.request<T>(config).pipe(rxTimeout(this.timeoutMs)),
        );

        mcpLogger.info(`[InventoryClient] Response Received from ${url}`, {
          status: response.status,
          data: response.data,
        });

        return response.data;
      } catch (error: unknown) {
        const { status, message, detail, isTimeout, isNetworkError } =
          this.extractErrorInfo(error);
        const isServerError = status >= 500;
        const isRetryable = isServerError || isTimeout || isNetworkError;

        if (!isRetryable || attempt === this.maxRetries) {
          // No es retryable o ya agotamos los reintentos
          const errorStatus =
            status || (isTimeout ? 'TIMEOUT' : 'NETWORK_ERROR');

          mcpLogger.error(
            `[InventoryClient] API Error (${errorStatus}) from ${url} [attempt ${attempt}/${this.maxRetries}]`,
            detail,
          );

          if (isTimeout) {
            throw new Error(
              `Inventory API Timeout after ${this.timeoutMs}ms: ${url}`,
            );
          }

          throw new Error(`Inventory API Error (${status || 500}): ${message}`);
        }

        // Calcular delay con backoff exponencial: 1s, 2s, 4s (capped at 5s)
        const delay = Math.min(1000 * Math.pow(2, attempt - 1), 5000);

        this.logger.warn(
          `[InventoryClient] Retry ${attempt}/${this.maxRetries} for ${config.method} ${url} after ${delay}ms (status: ${status || 'network error'})`,
        );
        mcpLogger.warn(
          `[InventoryClient] Retry ${attempt}/${this.maxRetries} for ${url} after ${delay}ms`,
          { status, isTimeout, attempt },
        );

        await this.sleep(delay);
      }
    }

    // Unreachable, pero TypeScript lo necesita
    throw new Error(
      `Inventory API Error: max retries (${this.maxRetries}) exhausted for ${url}`,
    );
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  private extractErrorInfo(error: unknown): InventoryErrorInfo {
    const isAxiosLikeError = (
      candidate: unknown,
    ): candidate is {
      response?: { status?: number; data?: { message?: string } };
      code?: string;
      name?: string;
      message?: string;
    } => typeof candidate === 'object' && candidate !== null;

    if (!isAxiosLikeError(error)) {
      return {
        status: 500,
        message: 'Unknown error',
        detail: error,
        isTimeout: false,
        isNetworkError: false,
      };
    }

    const status = error.response?.status ?? 0;
    const message =
      error.response?.data?.message ?? error.message ?? 'Unknown error';
    const detail = error.response?.data ?? error.message;
    const isTimeout =
      error instanceof TimeoutError ||
      error.name === 'TimeoutError' ||
      error.code === 'ECONNABORTED';
    const isNetworkError =
      !error.response &&
      !isTimeout &&
      ['ECONNREFUSED', 'ENOTFOUND', 'ENETUNREACH', 'EAI_AGAIN'].includes(
        error.code ?? '',
      );

    return {
      status,
      message,
      detail,
      isTimeout,
      isNetworkError,
    };
  }

  async get<T>(
    path: string,
    userJwt?: string,
    params?: Record<string, unknown>,
  ): Promise<T> {
    return this.request<T>('GET', path, undefined, userJwt, params);
  }

  async post<T>(path: string, data: unknown, userJwt?: string): Promise<T> {
    return this.request<T>('POST', path, data, userJwt);
  }
}
