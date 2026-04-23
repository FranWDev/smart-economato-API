import * as fs from 'fs';
import * as path from 'path';

const LOGS_DIR = path.join(process.cwd(), 'logs');
if (!fs.existsSync(LOGS_DIR)) {
  fs.mkdirSync(LOGS_DIR, { recursive: true });
}

function getLogFilePath() {
  const date = new Date().toISOString().split('T')[0];
  return path.join(LOGS_DIR, `mcp-${date}.md`);
}

function writeToLogFile(level: string, message: string, meta?: unknown) {
  const timestamp = new Date().toISOString();
  const filePath = getLogFilePath();

  let markdownEntry = `### [${level}] ${timestamp}\n`;
  markdownEntry += `${message}\n\n`;

  if (meta !== undefined && meta !== '') {
    markdownEntry +=
      '<details><summary>Ver detalles (JSON)</summary>\n\n```json\n';
    if (typeof meta === 'string') {
      try {
        const parsed: unknown = JSON.parse(meta);
        markdownEntry += JSON.stringify(parsed, null, 2) + '\n';
      } catch {
        markdownEntry += meta + '\n';
      }
    } else if (meta instanceof Error) {
      markdownEntry += meta.stack || meta.message + '\n';
    } else {
      markdownEntry += JSON.stringify(meta, null, 2) + '\n';
    }
    markdownEntry += '```\n</details>\n\n';
  } else {
    markdownEntry += '---\n\n';
  }

  try {
    fs.appendFileSync(filePath, markdownEntry, 'utf8');
  } catch (err: unknown) {
    console.error('Error writing to log file:', err);
  }
}

export const mcpLogger = {
  info: (message: string, meta?: unknown) => {
    if (meta !== undefined) console.log(message, meta);
    else console.log(message);
    writeToLogFile('INFO', message, meta);
  },
  warn: (message: string, meta?: unknown) => {
    if (meta !== undefined) console.warn(message, meta);
    else console.warn(message);
    writeToLogFile('WARN', message, meta);
  },
  error: (message: string, meta?: unknown) => {
    if (meta !== undefined) console.error(message, meta);
    else console.error(message);
    writeToLogFile('ERROR', message, meta);
  },
};
