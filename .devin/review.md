# review.md — Smart Economato API  
  
## Principio Fundamental: Honestidad por Encima de Todo  
  
Este archivo es consumido por un LLM (Devin) para realizar code reviews.  
Como LLM, existe una tendencia natural a **priorizar encontrar problemas** por encima de admitir  
que no los hay. Esto genera falsos positivos, críticas inventadas y ruido innecesario.  
  
**Reglas inquebrantables:**  
  
- **Si no hay errores destacables, decirlo explícitamente.** No inventar problemas donde no existen.  
- **No fabricar issues para justificar la review.** Un "no se encontraron problemas significativos"  
  es una respuesta perfectamente válida y preferible a señalar falsos problemas.  
- **No exagerar la severidad de hallazgos menores.** Un detalle cosmético no es un bug crítico.  
- **Priorizar la precisión sobre la exhaustividad.** Es mejor reportar 2 problemas reales que  
  10 donde 8 son inventados o irrelevantes.  
- **Ante la duda, verificar antes de afirmar.** Si no estás seguro de que algo es un error,  
  investiga el contexto completo antes de reportarlo. Si tras investigar sigues sin estar seguro,  
  menciónalo como duda, no como error.  
  
> La credibilidad de la review depende de su honestidad. Una review que inventa problemas  
> es peor que no hacer review.  
  
---  
  
## Descripción del Proyecto  
  
Smart Economato API es un sistema de gestión de economato para cocinas educativas.  
Consta de dos servicios principales:  
  
- **inventory-service**: Backend Spring Boot 4.0 (Java 25) con arquitectura hexagonal  
- **predictor-service**: Microservicio Python/FastAPI con Prophet para predicción de stock  
  
Infraestructura: PostgreSQL (primary + replica CQRS), Redis, Kafka (KRaft), Nginx, Prometheus, Grafana.  
  
---  
  
## Arquitectura y Estructura de Paquetes  
  
El proyecto sigue **Arquitectura Hexagonal** (Ports & Adapters). Toda contribución debe respetar esta estructura:

inventory-service/src/main/java/com/economato/inventory/
├── domain/
│ ├── model/ # Entidades JPA puras. SIN dependencias de infraestructura.
│ ├── *Auditable.java # Interfaces de auditoría de dominio
│ └── PredictorTrigger.java
├── application/
│ ├── dto/ # DTOs de request, response y proyecciones
│ ├── mapper/ # MapStruct mappers
│ └── usecase/ # Servicios de aplicación (lógica de negocio)
└── infrastructure/
├── adapter/
│ ├── in/ # Adaptadores de entrada (controllers REST, consumers Kafka)
│ └── out/ # Adaptadores de salida (repositorios JPA, producers Kafka, servicios externos)
├── aspect/ # Aspectos AOP
├── config/
│ ├── cache/ # Configuración Redis
│ ├── database/ # Configuración datasources (writer/reader)
│ ├── messaging/ # Configuración Kafka
│ ├── resilience/ # Configuración Resilience4j
│ ├── security/ # JWT, RBAC, SecurityConfig
│ └── web/ # WebSocket, CORS, etc.
├── scheduler/ # Tareas programadas
└── *.java # Eventos de dominio (CircuitBreaker, WebSocket)

 

predictor-service/
├── app/
│ ├── core/ # Lógica central del predictor
│ ├── db/ # Persistencia SQLite (outbox)
│ ├── services/ # Servicios de predicción (Prophet)
│ └── main.py # Entry point FastAPI
└── tests/

### Reglas estrictas de capas  
  
- **domain/** nunca debe importar clases de `infrastructure/` ni de `application/`.  
- **application/usecase/** no debe depender directamente de implementaciones de infraestructura; debe usar interfaces (ports).  
- Los **Controllers** (`adapter/in/`) solo orquestan: delegan toda lógica a los servicios de `usecase/`.  
- Los **DTOs** nunca deben ser entidades JPA y viceversa. Usar mappers para la conversión.  
  
---  
  
## Patrones Críticos del Proyecto  
  
### 1. CQRS (Command Query Responsibility Segregation)  
  
El sistema usa dos datasources separados:  
- **Writer**: `postgres:5432` (primary) — para escrituras  
- **Reader**: `postgres-replica:5433` — para lecturas  
  
Al revisar código, verificar que:  
- Las operaciones de **lectura** usen el datasource reader  
- Las operaciones de **escritura** usen el datasource writer  
- No se mezclen lecturas y escrituras en la misma transacción de forma incorrecta  
  
### 2. Stock Ledger Criptográfico  
  
Cada movimiento de stock se registra en `StockLedger` con hash encadenado (tipo blockchain).  
Existe además una capa de `LedgerBlock` con verificación Merkle.  
  
Al revisar cambios en el ledger:  
- **Nunca** modificar registros existentes del ledger (es append-only)  
- Verificar que el HMAC se calcule correctamente con la versión de secreto configurada  
- Los servicios `StockLedgerService`, `BlockchainService` y `MerkleTreeService` son críticos para la integridad  
  
### 3. Resilience4j Circuit Breakers  
  
Hay circuit breakers configurados para: `db`, `replica`, `redis`, `kafka`.  
  
Al revisar código que interactúe con estos sistemas:  
- Verificar que las llamadas estén protegidas por el circuit breaker correspondiente  
- Los fallbacks deben degradar gracefully (no lanzar excepciones no controladas)  
- Solo errores de **conexión** deben abrir el circuito (no errores de lógica de negocio)  
  
### 4. Kafka y Outbox Pattern  
  
La comunicación entre inventory-service y predictor-service usa Kafka.  
Se implementa el patrón **Transactional Outbox** (`AuditOutbox`) para garantizar consistencia.  
  
Al revisar:  
- Los eventos deben escribirse primero en la tabla outbox dentro de la misma transacción  
- El procesador de outbox se encarga de publicar a Kafka de forma asíncrona  
- El predictor-service usa SQLite como outbox local para sus respuestas  
  
### 5. Seguridad (JWT + RBAC)  
  
- Autenticación stateless con JWT  
- Roles jerárquicos gestionados por `UserService` y `RoleEscalationSchedulerService`  
- Tokens revocados se almacenan en Redis (con fallback in-memory)  
  
Al revisar endpoints nuevos:  
- Verificar que tengan las anotaciones de autorización correctas  
- No exponer datos sensibles en responses  
- Validar que los tokens revocados se respeten  
  
### 6. Prevención de N+1 Profundos  
  
El proyecto ya usa `JOIN FETCH` y `@EntityGraph` extensivamente en los repositorios.  
Al revisar código nuevo, verificar que no se introduzcan patrones N+1 sutiles:  
  
- **Llamadas a repositorio dentro de bucles**: Nunca hacer `repository.findById()` o similar  
  dentro de un `for`/`forEach`/`stream().map()`. Usar consultas batch (`findByIdIn`, etc.).  
- **Acceso a relaciones lazy fuera de transacción**: Acceder a `entity.getRelacion()` fuera  
  del contexto `@Transactional` dispara `LazyInitializationException` o una consulta extra  
  si el proxy sigue abierto.  
- **Acceso a valores en Maps/colecciones lazy**: Iterar o hacer `.get()` sobre colecciones  
  `@OneToMany`/`@ManyToMany` que no fueron cargadas con fetch join genera una consulta por  
  cada acceso.  
- **Cadenas de navegación profundas**: Expresiones como `batch.getProduct().getSupplier().getName()`  
  pueden disparar múltiples consultas si las relaciones intermedias son lazy.  
- **Mapeo manual en streams sin prefetch**: Si se mapean entidades a DTOs en un stream y se  
  accede a relaciones lazy dentro del `.map()`, cada iteración dispara una consulta.  
  Preferir cargar todo con `JOIN FETCH` o hacer un prefetch previo.  
  
> **Ejemplo existente correcto**: `StockAlertService.getDailyForecast()` carga los batches  
> activos con una sola llamada a `productBatchService.getActiveBatches()` en vez de navegar  
> la relación lazy desde el forecast.  
  
---  
  
## Convenciones de Código  
  
### Java (inventory-service)  
  
- **Java 25** con `--enable-preview`. Se pueden usar virtual threads y features experimentales.  
- **Spring Boot 4.0** con Jackson 3 (compatibilidad Jackson 2 habilitada).  
- Usar `@Transactional(readOnly = true)` en operaciones de solo lectura.  
- Inyección de dependencias por **constructor** (no `@Autowired` en campos).  
- Los servicios deben ser **thread-safe** (virtual threads = alta concurrencia).  
- Nombrar DTOs con sufijos: `*RequestDTO`, `*ResponseDTO`, `*ProjectionDTO`.  
  
### Python (predictor-service)  
  
- FastAPI con Prophet para forecasting.  
- Comunicación vía Kafka (consume eventos, produce predicciones).  
- SQLite para persistencia local del outbox.  
- Verificar que los modelos Prophet no se entrenen en el hilo principal del event loop.  
  
### Imports y Comentarios  
  
- **No usar imports inline (fully-qualified class names en el código)**. Siempre importar  
  la clase en la cabecera del archivo. Solo se permite el uso de FQN cuando hay conflicto  
  de nombres entre dos clases con el mismo nombre simple (ej: `java.util.Date` vs  
  `java.sql.Date`), y en ese caso documentar el motivo con un comentario.  
- **Comentarios en español**. Todo comentario, Javadoc y documentación inline debe estar  
  en español. No se permiten comentarios en inglés innecesarios. Los únicos textos en  
  inglés aceptables son: nombres de variables/métodos, anotaciones, y mensajes de log  
  técnicos si aplica.  
  
---  
  
## Testing  
  
El proyecto tiene varias capas de tests:  
  
| Tipo | Ubicación | Qué verificar |  
|------|-----------|----------------|  
| **Unit tests** | `application/usecase/`, `application/dto/`, `application/mapper/` | Lógica de negocio aislada con mocks |  
| **Integration tests** | `infrastructure/adapter/`, `infrastructure/config/` | Interacción con DB, Redis, Kafka |  
| **Resilience tests** | `CircuitBreakerIntegrationTest`, `ResilienceEndToEndTest` | Comportamiento ante fallos de infra |  
| **Concurrency tests** | `ConcurrencyTest` | Race conditions y thread safety |  
  
### Reglas de testing para PRs  
  
- Todo servicio nuevo en `usecase/` debe tener tests unitarios.  
- Cambios en controllers deben tener tests de integración.  
- Cambios en el ledger o blockchain requieren tests que verifiquen integridad criptográfica.  
- Usar `TestDataUtil` y `DatabaseCleaner` para tests de integración.  
- El perfil de test usa `application-test.properties`.  
  
---  
  
## Docker y Despliegue  
  
- Todo se orquesta con `docker-compose.yml`.  
- Los volúmenes son **externos** (deben crearse manualmente antes del primer `docker-compose up`).  
- El backend usa **ZGC Generational** con límites de memoria: `-Xms192m -Xmx384m`.  
- Verificar que cambios en configuración se reflejen tanto en `application.properties` como en las variables de entorno del `docker-compose.yml`.  
  
---  
  
## Internacionalización (i18n)  
  
Los mensajes están en `inventory-service/src/main/resources/i18n/` con soporte para minimo ES y EN:  
- `messages*.properties` — mensajes generales de la aplicación  
- `ValidationMessages*.properties` — mensajes de validación  
  
Al añadir nuevos mensajes, incluirlos al menos en el archivo base (`.properties`) y en `_es.properties`.  
  
---  
  
## Checklist de Review  
  
- [ ] Respeta la arquitectura hexagonal (no hay imports cruzados entre capas)  
- [ ] Las operaciones de lectura usan el datasource reader  
- [ ] No se modifican registros existentes del StockLedger  
- [ ] Los nuevos endpoints tienen autorización configurada  
- [ ] Circuit breakers aplicados en llamadas a sistemas externos  
- [ ] DTOs separados de entidades, con mappers  
- [ ] Tests unitarios para lógica nueva  
- [ ] Sin secretos hardcodeados (usar variables de entorno)  
- [ ] Mensajes i18n en `i18n/messages` y `i18n/ValidationMessages`  
- [ ] Thread safety verificada (virtual threads activos)  
- [ ] Cambios en Kafka usan el patrón outbox  
- [ ] Sin patrones N+1: no hay llamadas a repositorio en bucles, ni acceso a relaciones lazy sin prefetch  
- [ ] Sin imports inline (FQN en el código) salvo conflicto de nombres documentado  
- [ ] Comentarios y documentación en español (no en inglés salvo tecnisismos)  
- [ ] **Honestidad**: si no se encontraron problemas reales, no inventarlos