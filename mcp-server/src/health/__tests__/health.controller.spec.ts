import { Test, TestingModule } from '@nestjs/testing';
import { HealthController } from '../health.controller';
import { ConfigService } from '@nestjs/config';
import { HttpService } from '@nestjs/axios';
import { ServiceUnavailableException } from '@nestjs/common';
import { of, throwError } from 'rxjs';
import { AxiosHeaders, AxiosResponse } from 'axios';

describe('HealthController', () => {
  let controller: HealthController;
  let getMock: jest.Mock;

  beforeEach(async () => {
    getMock = jest.fn();
    const module: TestingModule = await Test.createTestingModule({
      controllers: [HealthController],
      providers: [
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn().mockReturnValue('http://localhost:8080'),
          },
        },
        {
          provide: HttpService,
          useValue: {
            get: getMock,
          },
        },
      ],
    }).compile();

    controller = module.get<HealthController>(HealthController);
  });

  describe('liveness', () => {
    it('should return status ok with timestamp', () => {
      const result = controller.liveness();

      expect(result).toHaveProperty('status', 'ok');
      expect(result).toHaveProperty('timestamp');
      expect(typeof result.timestamp).toBe('string');
    });
  });

  describe('readiness', () => {
    it('should return ready when backend is reachable', async () => {
      const mockResponse: AxiosResponse = {
        data: { status: 'UP' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: { headers: new AxiosHeaders() },
      };
      getMock.mockReturnValue(of(mockResponse));

      const result = await controller.readiness();

      expect(result).toHaveProperty('status', 'ready');
      expect(result).toHaveProperty('backend', 'reachable');
      expect(getMock).toHaveBeenCalledWith(
        'http://localhost:8080/actuator/health',
      );
    });

    it('should throw ServiceUnavailableException when backend is unreachable', async () => {
      getMock.mockReturnValue(throwError(() => new Error('ECONNREFUSED')));

      await expect(controller.readiness()).rejects.toThrow(
        ServiceUnavailableException,
      );
    });

    it('should include error message in exception response', async () => {
      getMock.mockReturnValue(
        throwError(() => new Error('Connection timeout')),
      );

      try {
        await controller.readiness();
        fail('Should have thrown');
      } catch (error) {
        expect(error).toBeInstanceOf(ServiceUnavailableException);
        const response = (error as ServiceUnavailableException).getResponse();
        expect(response).toHaveProperty('status', 'not_ready');
        expect(response).toHaveProperty('backend', 'unreachable');
        expect(response).toHaveProperty('error', 'Connection timeout');
      }
    });
  });
});
