import { Module } from '@nestjs/common';
import { ProviderRegistry } from './provider-registry';

@Module({
  providers: [ProviderRegistry],
  exports: [ProviderRegistry],
})
export class AiProvidersModule {}
