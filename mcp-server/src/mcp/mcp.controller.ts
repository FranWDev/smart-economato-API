import {
  Controller,
  Post,
  Req,
  Res,
  Body,
  UseGuards,
  HttpCode,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { McpAuthGuard } from '../common/guards/mcp-auth.guard';
import { NestCompletionRequestDto } from '../common/dto/nest-completion-request.dto';
import { McpService } from './mcp.service';

@Controller()
export class McpController {
  constructor(private readonly mcpService: McpService) {}

  @Post('/api/completion')
  @HttpCode(200)
  @UseGuards(McpAuthGuard)
  async completion(
    @Req() req: Request,
    @Body() body: NestCompletionRequestDto,
    @Res() res: Response,
  ) {
    // Configurar cabeceras SSE manualmente para evitar conflictos con decoradores NestJS
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');

    const userJwt = req.headers['authorization'];
    await this.mcpService.streamCompletion(body, userJwt || '', res);
  }
}
