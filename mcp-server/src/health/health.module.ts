import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { HealthController } from './health.controller';

@Module({
  imports: [HttpModule],
  controllers: [HealthController],
})
export class HealthModule {}
