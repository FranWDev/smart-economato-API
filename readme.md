[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/FranWDev/turing-backend)

# Turing Backend - Smart Economato API 🛒👨‍🍳

Bienvenidos al Backend del **Sistema de Gestión de Economato Inteligente (Smart Economato)**. Este proyecto esta diseñado y construido con una arquitectura moderna de alta escalabilidad, seguridad extrema y un enfoque puramente orientado a eventos.

🌍 **¡Pruébalo en vivo!** Puedes acceder al cliente de producción conectado con este backend aquí: **[Smart Economato Live](https://economato.servehttp.com/cliente/)**

---

##  Arquitectura y Características Destacadas

Este proyecto va mucho más allá de un CRUD tradicional, implementando patrones de diseño avanzados y soluciones puras de grado empresarial:

- � **Sistema de Alertas Predictivas de Stock Bajo:** Este motor utiliza el algoritmo **Holt-Winters (Triple Exponential Smoothing)** para predecir la demanda de los próximos 14 días. Detecta anomalías mediante **Z-Score** y genera alertas categorizadas (de baja a crítica) antes de que ocurra el desabastecimiento.
- 🚀 **Java 25 con Virtual Threads (Project Loom):** Aprovechamiento masivo de hilos virtuales nativos de la JVM para el procesamiento asíncrono de predicciones y manejo de miles de conexiones concurrentes sin agotar recursos.
- 🛡️ **Stock Ledger Inmutable (Criptográfico):** Absolutamente cada movimiento de stock se registra en una cadena inmutable con hashes criptográficos entrelazados. Incluye funciones de verificación de integridad (`verify-all`) y snapshots `O(1)` para acceso ultrarrápido.
- ⚛️ **Transacciones Atómicas Batch:** Modificación masiva de stock con validaciones estrictas y capacidad de *rollback* automático.
- 🗄️ **Arquitectura CQRS a Nivel de Base de Datos:** Configuración real con **PostgreSQL Primario (Escritura)** y **PostgreSQL Réplica (Lectura)** balanceando la carga mediante múltiples *DataSources*.
- ⚡ **Caché Distribuida con Redis:** Respuestas ultrarrápidas y descarga agresiva sobre la base de datos principal, ideal para catálogos y búsquedas altamente concurrentes.
- 📨 **Event-Driven Architecture (Apache Kafka):** Los procesos pesados y las notificaciones asíncronas fluyen a través de topics de Kafka asegurando la tolerancia a fallos.
- 🔐 **Seguridad Avanzada (RBAC + JWT):** Sistema de control de acceso basado en roles granulares (Admin, Chef, Usuario) blindando todas las rutas de la API, junto con auditoría total de operaciones sobre Recetas e Inventario.

---

## 🛠️ Stack Tecnológico

- **Lenguaje:** Java 25
- **Framework Principal:** Spring Boot 4.0.0 (Spring Web, Security, Data JPA, Cache)
- **Base de Datos Principal:** PostgreSQL 16 (Patrón Primary-Replica)
- **Sistemas Distribuidos:** Apache Kafka, Apache Zookeeper
- **Caché y Estructuras en Memoria:** Redis 7
- **Data Mapping & Boilerplate:** MapStruct, Lombok
- **Documentación Viva:** Springdoc OpenAPI (Swagger y Scalar UI)
- **Despliegue e Infraestructura:** Docker & Docker Compose

---

## � Puesta en Marcha (Docker)

El proyecto completo está orquestado con **Docker Compose** e incluye PostgreSQL, Réplica de Postgres, Redis, Kafka, Zookeeper, y el propio Backend.

### Prerrequisitos
- Tener **Docker** y **Docker Compose** instalados en tu máquina.
- Liberar los puertos `8081` (Backend), `5432`/`5433` (Postgres), `6381` (Redis), `9092` (Kafka).

### Inicio Rápido (Servicios de un click)

En la raíz del proyecto, ejecuta el script proporcionado para automatizar la generación de imágenes y el arranque en orden:

```bash
./start-docker.sh
```
> **Nota:** El script verificará la salud de Data Bases y Brokers antes de arrancar la aplicación Java. 

**Opciones del Script:**
- Reconstruir e iniciar de cero borrando volúmenes temporales:
  ```bash
  ./start-docker.sh --clean
  ```

---

## 🌐 Endpoints y Vistas Integradas

Una vez que el script finaliza y todos los contenedores están `healthy`, tienes acceso local a todo el entorno:

- 🟢 **Backend API Base:** `http://localhost:8081`

- 📝 **Documentación Dinámica (Scalar UI):** [`http://localhost:8081/scalar`](http://localhost:8081/scalar)
- 🖥️ **Kafka UI (Gestor de Mensajes):** `http://localhost:8090`
- 🎯 **Redis Commander (Gestor de Caché):** `http://localhost:8091`

---

##  Credenciales de Prueba

Para probar la plataforma base de manera local, puedes utilizar el endpoint de autenticación con las credenciales por defecto:

- **Usuario:** `admin`
- **Contraseña:** `admin`

*(Estas credenciales deben ser actualizadas inmediatamente en un entorno expuesto a producción).*

---

## 📂 Estructura Principal del Proyecto (Dominio)

- `controller/`: Expone de forma limpia todas las APIS REST.
- `service/`: El corazón del negocio (Lógica CQRS, Cadenas del Ledger, Publicación a Kafka).
- `repository/`: Repositorios JPA inyectados condicionalmente de las Sources Writer/Reader.
- `model/` & `dto/`: Separación estricta entre Entidades JPA DB-Bound y objetos de transferencia de datos.
- `mapper/`: Interfaces declarativas ultra rápidas procesadas en compilación.
- `audit/` & `aspect/`: Aspectos AOP para rastrear quién hizo qué transparentemente.