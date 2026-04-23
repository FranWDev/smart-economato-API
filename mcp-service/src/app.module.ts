import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { HttpModule } from '@nestjs/axios';
import { McpModule } from './mcp/mcp.module';
import { HealthModule } from './health/health.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),
    HttpModule.register({
      timeout: 30000,
      maxRedirects: 3,
    }),
    McpModule,
    HealthModule,
  ],
})
export class AppModule {}
