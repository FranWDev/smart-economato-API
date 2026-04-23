import { Logger } from '@nestjs/common';
import Anthropic from '@anthropic-ai/sdk';
import {
  ILlmProvider,
  LlmEvent,
  LlmCompletionParams,
} from '../common/interfaces/llm-provider.interface';
import { mcpLogger } from '../common/logging/logger';

interface PendingToolUse {
  id: string;
  name: string;
  input: string;
}

interface TextBlock {
  type: 'text';
  text: string;
}

interface ToolUseBlock {
  type: 'tool_use';
  id: string;
  name: string;
  input: Record<string, unknown>;
}

type ContentBlock = TextBlock | ToolUseBlock;

interface ToolResultBlock {
  type: 'tool_result';
  tool_use_id: string;
  content: string;
  is_error?: boolean;
}

export class AnthropicProvider implements ILlmProvider {
  private readonly logger = new Logger(AnthropicProvider.name);
  private readonly client: Anthropic;

  constructor(apiKey: string) {
    this.client = new Anthropic({ apiKey });
  }

  async *streamCompletion(
    params: LlmCompletionParams,
    tools: Array<Record<string, unknown>>,
    executeTool: (
      name: string,
      args: Record<string, unknown>,
    ) => Promise<unknown>,
  ): AsyncIterable<LlmEvent> {
    mcpLogger.info(`[AnthropicProvider] Initializing stream`, {
      model: params.model,
      contextLength: params.compressedContext.length,
      toolsCount: tools.length,
    });

    // Map tools to Anthropic format
    const anthropicTools: Anthropic.Messages.Tool[] =
      tools.length > 0
        ? tools.map((t) => ({
            name: typeof t.name === 'string' ? t.name : '',
            description: typeof t.description === 'string' ? t.description : '',
            input_schema: {
              type: 'object',
              properties:
                (t.inputSchema as { properties?: Record<string, unknown> })
                  ?.properties ?? {},
              required:
                (t.inputSchema as { required?: string[] })?.required ?? [],
            },
          }))
        : [];

    let fullText = '';
    let keepRunning = true;
    let iteration = 0;
    let totalInputTokens = 0;
    let totalOutputTokens = 0;

    // Message history for Anthropic (will be filled if there are tool calls)
    const messages: Anthropic.Messages.MessageParam[] = [];
    messages.push({
      role: 'user',
      content: params.compressedContext,
    });

    while (keepRunning) {
      iteration++;
      mcpLogger.info(`[AnthropicProvider] Starting LLM iteration ${iteration}`);

      try {
        const stream = this.client.messages.stream({
          model: params.model,
          system: params.systemPrompt,
          messages,
          tools: anthropicTools.length > 0 ? anthropicTools : undefined,
          max_tokens: 8192,
          temperature: params.temperature ?? 0.3,
        });

        let assistantContent = '';
        let hasToolUse = false;
        // Record tool uses by index for safety
        const currentToolUses: Record<number, PendingToolUse> = {};

        for await (const event of stream) {
          if (event.type === 'content_block_start') {
            if (event.content_block.type === 'tool_use') {
              hasToolUse = true;
              currentToolUses[event.index] = {
                id: event.content_block.id,
                name: event.content_block.name,
                input: '',
              };
            }
          }

          if (event.type === 'content_block_delta') {
            if (event.delta.type === 'text_delta') {
              const text = event.delta.text;
              assistantContent += text;
              fullText += text;
              yield { type: 'text', content: text };
            } else if (event.delta.type === 'input_json_delta') {
              const toolUse = currentToolUses[event.index];
              if (toolUse) {
                toolUse.input += event.delta.partial_json;
              }
            }
          }

          if (event.type === 'message_start') {
            totalInputTokens += event.message.usage?.input_tokens ?? 0;
          }

          if (event.type === 'message_delta') {
            if (event.usage) {
              totalOutputTokens += event.usage.output_tokens ?? 0;
            }
          }
        }

        const toolUsesArray = Object.values(currentToolUses);

        if (assistantContent) {
          mcpLogger.info(
            `[AnthropicProvider] Assistant Response (Iteration ${iteration})`,
            assistantContent,
          );
        }

        if (hasToolUse) {
          mcpLogger.info(
            `[AnthropicProvider] Model requested ${toolUsesArray.length} tool calls`,
            toolUsesArray,
          );

          // Build the assistant message with text and tool uses
          const assistantMessageContent: ContentBlock[] = [];
          if (assistantContent) {
            assistantMessageContent.push({
              type: 'text',
              text: assistantContent,
            });
          }

          toolUsesArray.forEach((tu) => {
            assistantMessageContent.push({
              type: 'tool_use',
              id: tu.id,
              name: tu.name,
              input: JSON.parse(tu.input || '{}') as Record<string, unknown>,
            });
          });

          messages.push({
            role: 'assistant',
            content: assistantMessageContent,
          });

          // Execute tools and collect results
          const toolResultBlocks: ToolResultBlock[] = [];

          for (const tu of toolUsesArray) {
            const args = JSON.parse(tu.input || '{}') as Record<
              string,
              unknown
            >;
            yield {
              type: 'tool_call',
              toolCallId: tu.id,
              toolName: tu.name,
              arguments: args,
            };

            try {
              mcpLogger.info(`[AnthropicProvider] Executing tool: ${tu.name}`, {
                arguments: tu.input,
              });
              const result = await executeTool(tu.name, args);
              mcpLogger.info(
                `[AnthropicProvider] Tool ${tu.name} result`,
                result,
              );

              yield { type: 'tool_result', toolCallId: tu.id, result };

              toolResultBlocks.push({
                type: 'tool_result',
                tool_use_id: tu.id,
                content: JSON.stringify(result),
              });
            } catch (toolError: unknown) {
              const toolErrorMessage =
                toolError instanceof Error
                  ? toolError.message
                  : 'Unknown error';
              const errorMessage = `Error executing tool ${tu.name}: ${toolErrorMessage}`;
              this.logger.error(errorMessage);
              mcpLogger.error(errorMessage, toolError);

              toolResultBlocks.push({
                type: 'tool_result',
                tool_use_id: tu.id,
                content: JSON.stringify({ error: toolErrorMessage }),
                is_error: true,
              });
            }
          }

          messages.push({
            role: 'user',
            content:
              toolResultBlocks as unknown as Anthropic.Messages.ToolResultBlockParam[],
          });
          // The loop will continue so Claude can analyze the results
        } else {
          mcpLogger.info(`[AnthropicProvider] LLM loop finished`);
          keepRunning = false;
        }
      } catch (error: unknown) {
        const streamError =
          error instanceof Error ? error.message : 'Unknown error';
        const errorMessage = `Error in Anthropic stream: ${streamError}`;
        this.logger.error(errorMessage);
        mcpLogger.error(errorMessage, error);

        yield { type: 'error', message: streamError };
        keepRunning = false;
      }
    }

    mcpLogger.info(`[AnthropicProvider] Full generated response`, fullText);

    yield {
      type: 'done',
      fullText,
      inputTokens: totalInputTokens,
      outputTokens: totalOutputTokens,
    };
  }
}
