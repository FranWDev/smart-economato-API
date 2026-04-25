# Smart Economato API

Plataforma de gestión de inventario para escuelas culinarias que automatiza el control de stock, costeo de recetas, aprovisionamiento y planificación semanal de menús. Integra un asistente de IA conversacional y predicción de demanda mediante series temporales.

## Arquitectura

El sistema se basa en un **monolito modular con arquitectura hexagonal** (Ports & Adapters) como núcleo central (`inventory-service`), complementado por **servicios satélite políglotas** para capacidades que requieren ecosistemas específicos.

```mermaid
graph TD
    subgraph "Frontend"
        FE["Angular SPA"]
    end

    subgraph "Reverse Proxy"
        NG["Nginx"]
    end

    subgraph "Monolito Hexagonal"
        BE["inventory-service (Spring Boot 4.0 / Java 25)"]
    end

    subgraph "Servicios Satélite Políglotas"
        MCP["mcp-service (NestJS)"]
        PRED["predictor-service (Python / Prophet)"]
    end

    subgraph "Datos y Mensajería"
        PG["PostgreSQL 16 (Primary)"]
        PGR["PostgreSQL 16 (Replica)"]
        RD["Redis 7"]
        KF["Kafka (KRaft)"]
    end

    subgraph "Observabilidad"
        PROM["Prometheus"]
        GRAF["Grafana"]
    end

    FE -- "HTTPS" --> NG
    NG -- "/api/" --> BE
    NG -- "/" --> FE
    NG -- "/monitor/" --> GRAF
    BE -- "Write SQL" --> PG
    BE -- "Read SQL" --> PGR
    PG -- "Replicación WAL" --> PGR
    BE -- "Cache / JWT Blacklist" --> RD
    BE -- "Eventos (Outbox)" --> KF
    KF -- "Predicciones" --> PRED
    BE -- "HTTP (X-Service-Key)" --> MCP
    MCP -- "SSE" --> BE
    PROM -- "Scrape /actuator/prometheus" --> BE
    GRAF -- "Queries" --> PROM
```

### Servicios

| Servicio | Tecnología | Puerto interno | Descripción |
|---|---|---|---|
| **inventory-service** | Spring Boot 4.0, Java 25 | `8081` | **Monolito hexagonal**: todos los dominios de negocio (inventario, pedidos, recetas, planificación, ledger, blockchain, incidencias, usuarios). |
| **mcp-service** | NestJS 11, TypeScript | `3000` | **Servicio satélite**: Agente IA (Model Context Protocol) — conecta con OpenAI, Anthropic, Google, etc. |
| **predictor-service** | FastAPI, Python, Prophet | `8000` | **Servicio satélite**: Predicción de demanda con series temporales. |
| **frontend-service** | Angular + Nginx | `80` | SPA del cliente |
| **reverse-proxy** | Nginx 1.27 | `80/443` | Terminación SSL, enrutamiento |
| **postgres** | PostgreSQL 16 Alpine | `5432` | Base de datos primaria (escritura) |
| **postgres-replica** | PostgreSQL 16 Alpine | `5433` | Réplica de lectura (CQRS) |
| **redis** | Redis 7 Alpine | `6379` | Caché, blacklist JWT |
| **kafka** | Confluent Kafka 7.6 (KRaft) | `9092` | Mensajería event-driven, auditoría |
| **prometheus** | Prometheus 2.51 | `9090` | Recolección de métricas |
| **grafana** | Grafana 10.4 | `3000` | Dashboards de monitorización |

## Requisitos previos

- **Docker Desktop** (con Docker Compose v2)
- **4 GB de RAM libre** mínimo (8 GB recomendado)
- **10 GB de espacio en disco** libre
- **PowerShell 5.1+** (Windows) para el script de instalación

## Instalación y despliegue

El proyecto incluye un **panel de control interactivo** (`install.ps1`) que automatiza toda la configuración:

```powershell
# Ejecutar como Administrador
.\install.ps1
```

El script realiza automáticamente:

1. **Verificación de hardware** (RAM, disco)
2. **Generación del archivo `.env`** con secretos criptográficamente seguros (JWT, HMAC, contraseñas de BD)
3. **Creación de certificados SSL** autofirmados
4. **Creación de volúmenes Docker** persistentes
5. **Configuración DNS local** (`smart-economato` en `/etc/hosts`)
6. **Despliegue de todos los contenedores** con `docker compose up`
7. **Carga del catálogo inicial** de productos (primer despliegue)
8. **Registro de tarea programada** en Windows Task Scheduler para arranque automático

### Despliegue manual (sin script)

```bash
# 1. Crear archivo .env con las variables requeridas (ver sección Variables de Entorno)
cp .env.example .env

# 2. Crear volúmenes externos
docker volume create turing-backend_postgres-data
docker volume create turing-backend_postgres-replica-data
docker volume create turing-backend_redis-data
docker volume create turing-backend_kafka-data
docker volume create turing-backend_prometheus-data
docker volume create turing-backend_grafana-data
docker volume create turing-backend_predictor-outbox-data
docker volume create turing-backend_uploads-data

# 3. Levantar servicios
docker compose -p smart-economato-api up -d --build
```

## Variables de entorno

El archivo `.env` en la raíz del proyecto debe contener:

| Variable | Descripción | Ejemplo |
|---|---|---|
| `POSTGRES_DB` | Nombre de la base de datos | `inventory` |
| `POSTGRES_USER` | Usuario de PostgreSQL | `inventory_user` |
| `POSTGRES_PASSWORD` | Contraseña de PostgreSQL | *(generada automáticamente)* |
| `JWT_SECRET` | Clave secreta para tokens JWT (128 chars) | *(generada automáticamente)* |
| `JWT_EXPIRATION` | Expiración del JWT en ms | `86400000` (24h) |
| `LEDGER_HMAC_SECRET` | Clave HMAC para integridad del ledger | *(generada automáticamente)* |
| `SEED_ADMIN_NAME` | Nombre del administrador inicial | `Admin` |
| `SEED_ADMIN_USER` | Usuario del administrador inicial | `admin` |
| `SEED_ADMIN_PASSWORD` | Contraseña del administrador inicial | `admin123` |
| `AI_NEST_SERVICE_KEY` | Clave de comunicación inter-servicio con MCP | *(generada automáticamente)* |
| `AI_NEST_ALLOWED_ORIGIN` | Origen permitido para CORS del servicio IA | `https://localhost` |
| `GRAFANA_USER` | Usuario de Grafana | *(mismo que admin)* |
| `GRAFANA_PASSWORD` | Contraseña de Grafana | *(misma que admin)* |
| `PROXY_HTTP_PORT` | Puerto HTTP del proxy | `3000` |
| `PROXY_HTTPS_PORT` | Puerto HTTPS del proxy | `3443` |

## Estructura del proyecto

```
smart-economato-API/
├── inventory-service/          # Monolito modular (Spring Boot 4.0 / Java 25)
│   └── src/main/java/com/economato/inventory/
│       ├── domain/model/          # Núcleo: entidades de dominio puras
│       ├── application/           # Puertos: casos de uso, DTOs, mappers
│       │   ├── usecase/
│       │   ├── dto/
│       │   └── mapper/
│       └── infrastructure/        # Adaptadores
│           └── adapter/
│               ├── in/web/        # Driving: controladores REST
│               ├── in/messaging/  # Driving: consumidores Kafka
│               ├── out/persistence/ # Driven: repositorios JPA
│               ├── out/external/    # Driven: clientes HTTP
│               └── out/messaging/   # Driven: productores Kafka
├── mcp-service/                # Agente IA - Model Context Protocol (NestJS)
├── predictor-service/          # Predicción de demanda (FastAPI + Prophet)
├── nginx/                      # Configuración del reverse proxy
│   ├── reverse-proxy.conf      # Config Nginx para desarrollo
│   ├── reverse-proxy.template  # Template con variables de entorno
│   └── certs/                  # Certificados SSL (generados automáticamente)
├── grafana/                    # Provisioning de dashboards Grafana
├── docker-compose.yml          # Orquestación de todos los servicios
├── install.ps1                 # Panel de control e instalador (PowerShell)
├── reset.ps1                   # Script de reset del entorno
├── postgres-init.sh            # Inicialización de replicación PostgreSQL
├── productos.sql               # Catálogo inicial de productos
├── prometheus.yml              # Configuración de scraping de métricas
├── pg_hba.conf                 # Reglas de autenticación PostgreSQL
└── pom.xml                     # POM padre (Maven multi-módulo)
```

## Funcionalidades principales

### Gestión de inventario
- CRUD de productos con control de lotes y fechas de caducidad (FEFO)
- Trazabilidad completa con **ledger inmutable** protegido por HMAC
- **Blockchain** para sellado de bloques de movimientos de stock

### Recetas y planificación
- Gestión de recetas con ingredientes, costes y alérgenos
- Planificación semanal de menús con reserva automática de stock
- Generación de **informes PDF** (iText 8)

### Pedidos y aprovisionamiento
- Gestión de órdenes de compra
- Alertas automáticas de stock bajo

### Asistente de IA ("Chef Pio")
- Chat conversacional con soporte multi-proveedor: OpenAI, Anthropic, Google, DeepSeek, Grok, Groq
- **Semantic Memory Graph (SMG)** para compresión inteligente de contexto
- Detección de intenciones (stock, pedidos, recetas, costes, alérgenos, crisis)
- Streaming SSE en tiempo real
- Rate limiting configurable por usuario

### Predicción de demanda
- Series temporales con **Facebook Prophet**
- Comunicación event-driven vía Kafka
- Outbox persistente en SQLite

### Seguridad
- Autenticación JWT con blacklist en Redis
- Roles: `ADMIN`, `CHEF`, `STUDENT`
- Comunicación inter-servicio con `X-Service-Key`
- Cifrado AES de API keys de IA (AI Vault)
- Circuit breakers (Resilience4j) para DB, Redis, Kafka y servicio MCP

### Observabilidad
- Métricas Prometheus (JVM, HikariCP, Kafka, caché)
- Dashboards Grafana
- WebSockets para alertas y notificaciones en tiempo real

## Documentación de la API

Con el sistema en ejecución, la documentación interactiva está disponible en:

| Herramienta | URL |
|---|---|
| **Scalar** | `https://localhost/scalar` |
| **Swagger UI** | `https://localhost/swagger-ui.html` |
| **OpenAPI JSON** | `https://localhost/v3/api-docs` |

## Panel de control (`install.ps1`)

El script ofrece un menú interactivo con las siguientes opciones:

- **Configurar sistema** — Inicialización completa (primera vez)
- **Encender** — `docker compose up -d --build --wait`
- **Apagar** — `docker compose down`
- **Reiniciar** — `docker compose restart`
- **Diagnóstico** — Health checks, logs recientes, auto-reparación
- **Limpieza profunda** — Prune de imágenes y caché Docker
- **Backup/Restore** — Exportar e importar volúmenes Docker como `.tar.gz`

## Tests

```bash
# Tests unitarios (excluye tests lentos por defecto)
cd inventory-service
mvn test

# Todos los tests (incluye integración con Testcontainers)
mvn test -P all-tests
```

El proyecto usa:
- **JUnit 5** + **Mockito** para tests unitarios
- **Testcontainers** (PostgreSQL, Kafka, Redis) para tests de integración
- **JaCoCo** para cobertura de código
- **pytest** para el predictor-service

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend principal | Spring Boot 4.0, Java 25, Virtual Threads |
| Agente IA | NestJS 11, TypeScript, OpenAI SDK, Anthropic SDK |
| Predicción | FastAPI, Prophet, Pandas |
| Base de datos | PostgreSQL 16 (Primary + Replica) |
| Caché | Redis 7 |
| Mensajería | Apache Kafka (KRaft, sin Zookeeper) |
| Reverse Proxy | Nginx 1.27 |
| Monitorización | Prometheus + Grafana |
| Seguridad | Spring Security, JWT (jjwt), Resilience4j |
| Documentación API | SpringDoc OpenAPI + Scalar |
| PDF/Excel | iText 8, Apache POI 5.3 |
| Mapping | MapStruct 1.6 |
| Contenedores | Docker Compose |

## Licencia

Este proyecto es de uso privado (UNLICENSED).
