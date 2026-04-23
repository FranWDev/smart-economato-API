# MCP Server (Gestión de Inventario)

Servidor MCP (Model Context Protocol) basado en NestJS que actúa como puente entre LLMs (OpenAI, Anthropic, DeepSeek) y el backend de gestión de inventario Java.

## 🚀 Características
- **Arquitectura Stateless**: Orquestación pura sin persistencia local.
- **Streaming SSE**: Comunicación en tiempo real con eventos `token`, `tool_called`, `tool_result` y `done`.
- **Soporte Multi-Modelo**: Adaptadores para diferentes proveedores de IA.
- **Seguridad**: Autenticación mediante `X-Service-Key` y re-forwarding de JWT.
- **25 Tools de Lectura**: Análisis, búsqueda y consulta profunda de datos.

## 📁 Estructura del Proyecto
- `src/mcp`: Orquestador central y controlador SSE.
- `src/ai-providers`: Adaptadores para proveedores de LLM.
- `src/tools`: Definiciones y lógica de ejecución de herramientas MCP.
- `src/common`: Seguridad (Guards), DTOs e interfaces compartidas.

## ⚙️ Configuración
Crea un archivo `.env` en la raíz (usa `.env.example` como plantilla):
```env
PORT=3000
SERVICE_KEY=tu_clave_compartida
BACKEND_BASE_URL=https://tu-backend-java.com
```

## 🛠️ Herramientas Disponibles (Tools)
El servidor expone 25 herramientas organizadas en:
1. **Read Tools**: Detalle de productos, recetas, lotes, histórico de consumo, etc.
2. **Analysis Tools**: Sugerencias de pedido, optimización de menú, salud de stock.
3. **Utility Tools**: Búsqueda unificada, contexto global del sistema.

## 📡 Eventos SSE
El endpoint `POST /api/completion` emite los siguientes eventos:
- `thinking` / `thinking_delta`: (Opcional) Proceso de razonamiento.
- `tool_called`: Notificación de invación de herramienta.
- `tool_result`: Resultado JSON de la herramienta ejecutada.
- `token`: Fragmentos de texto de la respuesta.
- `done`: Finalización obligatoria con estadísticas de tokens.

## 📦 Ejecución
```bash
# Desarrollo
npm run start:dev

# Producción (Docker)
docker build -t mcp-server .
docker run -p 3000:3000 mcp-server
```

## 🛡️ Licencia
MIT
