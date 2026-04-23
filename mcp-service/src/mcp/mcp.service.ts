import { Inject, Injectable, Logger } from '@nestjs/common';
import { Response } from 'express';
import { NestCompletionRequestDto } from '../common/dto/nest-completion-request.dto';
import {
  LlmEvent,
  LlmCompletionParams,
} from '../common/interfaces/llm-provider.interface';
import { ProviderRegistry } from '../ai-providers/provider-registry';
import { InventoryClient } from './inventory.client';
import { mcpLogger } from '../common/logging/logger';
import { IMcpTool } from '../common/interfaces/mcp-tool.interface';

@Injectable()
export class McpService {
  private readonly logger = new Logger(McpService.name);

  constructor(
    private readonly providerRegistry: ProviderRegistry,
    private readonly inventoryClient: InventoryClient,
    @Inject('TOOL_REGISTRY')
    private readonly toolRegistry: Map<string, IMcpTool>,
  ) {}

  async streamCompletion(
    body: NestCompletionRequestDto,
    userJwt: string,
    res: Response,
  ): Promise<void> {
    const provider = this.providerRegistry.getProvider(
      body.provider,
      body.apiKey,
    );

    const tools = Array.from(this.toolRegistry.values()).map(
      (tool) => tool.definition as unknown as Record<string, unknown>,
    );

    try {
      mcpLogger.info(`Starting stream completion for user: ${body.userName}`, {
        model: body.model,
        provider: body.provider,
        contextLength: body.compressedContext.length,
      });

      const params: LlmCompletionParams = {
        systemPrompt: body.systemPrompt,
        compressedContext: body.compressedContext,
        model: body.model,
        temperature: body.temperature,
      };

      const stream = provider.streamCompletion(params, tools, (name, args) =>
        this.executeTool(name, args, userJwt),
      );

      for await (const event of stream) {
        this.emitSse(res, event);

        if (event.type === 'error') {
          mcpLogger.error(`Stream error in loop: ${event.message}`);
          break;
        }
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error(`Stream error: ${message}`);
      mcpLogger.error(`Top-level stream error: ${message}`, error);
      this.emitSse(res, { type: 'error', message });
    } finally {
      res.end();
    }
  }

  private async executeTool(
    name: string,
    args: unknown,
    userJwt: string,
  ): Promise<unknown> {
    this.logger.log(`Executing tool: ${name}`);

    const tool = this.toolRegistry.get(name);
    if (!tool) {
      throw new Error(`Tool "${name}" not found in registry`);
    }

    try {
      const validatedArgs = tool.schema.parse(args);
      const result = await tool.execute(
        validatedArgs,
        this.inventoryClient,
        userJwt,
      );

      mcpLogger.info(`Tool ${name} executed successfully`, { result });
      return result;
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      const errorMessage = `Error in tool ${name}: ${message}`;
      this.logger.error(errorMessage);
      mcpLogger.error(errorMessage, error);
      throw error;
    }
  }

  private emitSse(res: Response, event: LlmEvent): void {
    let sseEvent = '';

    switch (event.type) {
      case 'text':
        sseEvent = `event: token\ndata: ${JSON.stringify(event.content)}\n\n`;
        break;
      case 'tool_call':
        mcpLogger.info(`[McpService] Emitting SSE: tool_called`, event);
        sseEvent = `event: tool_called\ndata: ${JSON.stringify({ toolName: event.toolName, toolCallId: event.toolCallId })}\n\n`;
        break;
      case 'tool_result':
        mcpLogger.info(`[McpService] Emitting SSE: tool_result`, event);
        sseEvent = `event: tool_result\ndata: ${JSON.stringify(event.result)}\n\n`;
        break;
      case 'done':
        mcpLogger.info(`[McpService] Emitting SSE: done`, {
          fullTextLength: event.fullText.length,
        });
        sseEvent = `event: done\ndata: ${JSON.stringify({
          fullResponse: event.fullText,
          inputTokens: event.inputTokens,
          outputTokens: event.outputTokens,
        })}\n\n`;
        break;
      case 'error':
        mcpLogger.error(`[McpService] Emitting SSE: error`, event.message);
        sseEvent = `event: error\ndata: ${JSON.stringify(event.message)}\n\n`;
        break;
    }

    if (sseEvent) {
      res.write(sseEvent);
    }
  }
}
