import { Module } from '@nestjs/common';
import { McpController } from './mcp.controller';
import { McpService } from './mcp.service';
import { InventoryClient } from './inventory.client';
import { HttpModule } from '@nestjs/axios';
import { AiProvidersModule } from '../ai-providers/ai-providers.module';
import { ToolsModule } from '../tools/tools.module';

@Module({
  imports: [HttpModule, AiProvidersModule, ToolsModule],
  controllers: [McpController],
  providers: [McpService, InventoryClient],
  exports: [McpService, InventoryClient],
})
export class McpModule {}
