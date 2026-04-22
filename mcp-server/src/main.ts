import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { ValidationPipe } from '@nestjs/common';
import { RequestInterceptor } from './common/logging/request.interceptor';
import { SseExceptionFilter } from './common/filters/sse-exception.filter';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // Interceptor de logs global
  app.useGlobalInterceptors(new RequestInterceptor());

  // Exception filter global (maneja errores en contexto SSE)
  app.useGlobalFilters(new SseExceptionFilter());

  // CORS configurable desde variable de entorno
  const corsOrigins = process.env.CORS_ORIGINS;
  app.enableCors({
    origin: corsOrigins
      ? corsOrigins.split(',').map((origin) => origin.trim())
      : '*',
  });

  // Pipe de validación global
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  const port = process.env.PORT ?? 3000;
  await app.listen(port);
  console.log(`MCP Server running on http://localhost:${port}`);
}
void bootstrap();
