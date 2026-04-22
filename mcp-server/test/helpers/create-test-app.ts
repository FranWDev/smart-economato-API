import { HttpService } from '@nestjs/axios';
import { ValidationPipe } from '@nestjs/common';
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { AppModule } from '../../src/app.module';
import { ProviderRegistry } from '../../src/ai-providers/provider-registry';
import { SseExceptionFilter } from '../../src/common/filters/sse-exception.filter';

type CreateTestAppOptions = {
  providerRegistryMock?: Pick<ProviderRegistry, 'getProvider'>;
  httpServiceMock?: Pick<HttpService, 'get' | 'request'>;
};

export async function createTestApp(
  options: CreateTestAppOptions = {},
): Promise<INestApplication> {
  const moduleBuilder = Test.createTestingModule({
    imports: [AppModule],
  });

  if (options.providerRegistryMock) {
    moduleBuilder
      .overrideProvider(ProviderRegistry)
      .useValue(options.providerRegistryMock);
  }

  if (options.httpServiceMock) {
    moduleBuilder
      .overrideProvider(HttpService)
      .useValue(options.httpServiceMock);
  }

  const moduleRef = await moduleBuilder.compile();
  const app = moduleRef.createNestApplication();

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  app.useGlobalFilters(new SseExceptionFilter());

  await app.init();
  return app;
}
