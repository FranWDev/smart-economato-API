import { Logger } from '@nestjs/common';
import OpenAI from 'openai';
import {
  ILlmProvider,
  LlmEvent,
  LlmCompletionParams,
} from '../common/interfaces/llm-provider.interface';
import {
  ChatCompletionMessageParam,
  ChatCompletionToolMessageParam,
} from 'openai/resources/chat/completions';
import { mcpLogger } from '../common/logging/logger';

interface PendingToolCall {
  id: string;
  name: string;
  arguments: string;
}

export class OpenAiCompatibleProvider implements ILlmProvider {
  private readonly logger = new Logger(OpenAiCompatibleProvider.name);
  private readonly client: OpenAI;

  constructor(apiKey: string, baseUrl?: string) {
    this.client = new OpenAI({
      apiKey,
      ...(baseUrl ? { baseURL: baseUrl } : {}),
    });
  }

  async *streamCompletion(
    params: LlmCompletionParams,
    tools: Array<Record<string, unknown>>,
    executeTool: (
      name: string,
      args: Record<string, unknown>,
    ) => Promise<unknown>,
  ): AsyncIterable<LlmEvent> {
    const messages: ChatCompletionMessageParam[] = [
      { role: 'system', content: params.systemPrompt },
      { role: 'user', content: params.compressedContext },
    ];

    mcpLogger.info(`[OpenAiCompatibleProvider] Initializing stream`, {
      model: params.model,
      contextLength: params.compressedContext.length,
      toolsCount: tools.length,
    });

    const toolDefinitions =
      tools.length > 0
        ? tools.map((t) => ({
            type: 'function' as const,
            function: {
              name: typeof t.name === 'string' ? t.name : '',
              description:
                typeof t.description === 'string' ? t.description : '',
              parameters: (t.inputSchema as Record<string, unknown>) ?? {},
            },
          }))
        : undefined;

    let fullText = '';
    let keepRunning = true;
    let iteration = 0;
    let totalInputTokens = 0;
    let totalOutputTokens = 0;

    while (keepRunning) {
      iteration++;
      mcpLogger.info(
        `[OpenAiCompatibleProvider] Starting LLM iteration ${iteration}`,
      );

      try {
        const stream = await this.client.chat.completions.create({
          model: params.model,
          messages,
          tools: toolDefinitions,
          temperature: params.temperature ?? 0.3,
          stream: true,
          stream_options: { include_usage: true },
        });

        const currentToolCalls: PendingToolCall[] = [];
        let assistantContent = '';
        let hasToolCalls = false;

        for await (const chunk of stream) {
          // Capture usage from the last chunk
          if (chunk.usage) {
            totalInputTokens += chunk.usage.prompt_tokens ?? 0;
            totalOutputTokens += chunk.usage.completion_tokens ?? 0;
          }

          const delta = chunk.choices[0]?.delta;

          if (delta?.content) {
            assistantContent += delta.content;
            fullText += delta.content;
            yield { type: 'text', content: delta.content };
          }

          if (delta?.tool_calls) {
            hasToolCalls = true;
            for (const tc of delta.tool_calls) {
              if (!currentToolCalls[tc.index]) {
                currentToolCalls[tc.index] = {
                  id: '',
                  name: '',
                  arguments: '',
                };
              }
              if (tc.id) currentToolCalls[tc.index].id += tc.id;
              if (tc.function?.name)
                currentToolCalls[tc.index].name += tc.function.name;
              if (tc.function?.arguments)
                currentToolCalls[tc.index].arguments += tc.function.arguments;
            }
          }
        }

        if (assistantContent) {
          mcpLogger.info(
            `[OpenAiCompatibleProvider] Assistant Response (Iteration ${iteration})`,
            assistantContent,
          );
        }

        // If there were tool calls, execute them and continue the loop
        if (hasToolCalls) {
          mcpLogger.info(
            `[OpenAiCompatibleProvider] Model requested ${currentToolCalls.length} tool calls`,
            currentToolCalls,
          );

          // Add the assistant message with tool calls to history
          messages.push({
            role: 'assistant',
            content: assistantContent || null,
            tool_calls: currentToolCalls.map((tc) => ({
              id: tc.id,
              type: 'function',
              function: { name: tc.name, arguments: tc.arguments },
            })),
          });

          for (const tc of currentToolCalls) {
            if (!tc) continue;

            yield {
              type: 'tool_call',
              toolCallId: tc.id,
              toolName: tc.name,
              arguments: JSON.parse(tc.arguments || '{}') as Record<
                string,
                unknown
              >,
            };

            try {
              mcpLogger.info(
                `[OpenAiCompatibleProvider] Executing tool: ${tc.name}`,
                {
                  arguments: tc.arguments,
                },
              );
              const result = await executeTool(
                tc.name,
                JSON.parse(tc.arguments || '{}') as Record<string, unknown>,
              );
              const resultString = JSON.stringify(result);

              mcpLogger.info(
                `[OpenAiCompatibleProvider] Tool ${tc.name} result`,
                result,
              );
              yield { type: 'tool_result', toolCallId: tc.id, result };

              // Add the tool result to history
              messages.push({
                role: 'tool',
                tool_call_id: tc.id,
                content: resultString,
              } as ChatCompletionToolMessageParam);
            } catch (toolError: unknown) {
              const toolErrorMessage =
                toolError instanceof Error
                  ? toolError.message
                  : 'Unknown error';
              const errorMessage = `Error executing tool ${tc.name}: ${toolErrorMessage}`;
              this.logger.error(errorMessage);
              mcpLogger.error(errorMessage, toolError);

              messages.push({
                role: 'tool',
                tool_call_id: tc.id,
                content: JSON.stringify({ error: toolErrorMessage }),
              } as ChatCompletionToolMessageParam);
            }
          }
          // The loop will continue with a new OpenAI call including tool results
        } else {
          // No more tool calls, finish
          mcpLogger.info(`[OpenAiCompatibleProvider] LLM loop finished`);
          keepRunning = false;
        }
      } catch (error: unknown) {
        const streamError =
          error instanceof Error ? error.message : 'Unknown error';
        const errorMessage = `Error in OpenAI compatible stream: ${streamError}`;
        this.logger.error(errorMessage);
        mcpLogger.error(errorMessage, error);

        yield { type: 'error', message: streamError };
        keepRunning = false;
      }
    }

    mcpLogger.info(
      `[OpenAiCompatibleProvider] Full generated response`,
      fullText,
    );

    yield {
      type: 'done',
      fullText,
      inputTokens: totalInputTokens,
      outputTokens: totalOutputTokens,
    };
  }
}
