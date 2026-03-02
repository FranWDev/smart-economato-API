Plan de Implementación: Observabilidad Full-Stack (Prometheus + Grafana)

Proyecto: turing-backend | Stack: Java 25 / Spring Boot 4.0 / CQRS / Kafka
FASE 0 — Precondiciones de Infraestructura

Verificar antes de ejecutar para evitar fallos de contexto:
| Artefacto | Ruta | Estado / Requisito |
| :--- | :--- | :--- |
| Actuator | pom.xml | <artifactId>spring-boot-starter-actuator</artifactId> |
| Network | docker-compose.yml | Red inventory-network debe estar definida. |
| Secrets | .env | Poseer POSTGRES_PASSWORD y JWT_SECRET. |
FASE 1 — Dependencias (Micrometer)

Archivo: pom.xml
Acción: Insertar tras Actuator (línea 60).
XML

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>

FASE 2 — Configuración de Actuator (Hardened)

Archivo: src/main/resources/application.properties
Acción: Configuración de exposición selectiva y Probes de Kubernetes/Docker.
Properties

# --- ACTUATOR & OBSERVABILITY ---
# Exponer solo lo estrictamente necesario (Hardening)
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.probes.enabled=true
management.endpoint.prometheus.enabled=true
management.endpoint.health.show-details=never

# Tags Globales para Agregación en Grafana
management.metrics.tags.application=${spring.application.name}
management.metrics.tags.environment=production
management.metrics.tags.region=dc-1

# Monitoreo de Subsistemas Críticos
management.metrics.enable.hikaricp=true
management.metrics.enable.jvm=true
management.metrics.enable.kafka=true

FASE 3 — Métricas de Negocio (Custom Metrics)
3.1 — StockLedgerService (Performance & Integrity)

Archivo: src/main/java/com/economato/inventory/service/StockLedgerService.java
Inyección: Añadir MeterRegistry al constructor.
Java

// Métricas declaradas como final para thread-safety
private final Counter stockMovementsCounter;
private final Timer ledgerHashTimer;

public StockLedgerService(..., MeterRegistry meterRegistry) {
    // ... asignaciones previas ...
    this.stockMovementsCounter = Counter.builder("stock.ledger.movements.total")
        .description("Total de movimientos en el ledger criptográfico")
        .register(meterRegistry);

    this.ledgerHashTimer = Timer.builder("stock.ledger.hash.duration")
        .description("Latencia del cómputo SHA-256")
        .publishPercentiles(0.95, 0.99) // Crítico para detectar outliers en Virtual Threads
        .register(meterRegistry);
}

3.2 — AuditOutboxProcessor (Lag Detection)

Archivo: src/main/java/com/economato/inventory/scheduler/AuditOutboxProcessor.java
Java

public AuditOutboxProcessor(AuditOutboxRepository repo, MeterRegistry registry) {
    Gauge.builder("kafka.audit.outbox.pending", repo, r -> r.countByProcessed(false))
        .description("Eventos pendientes en Outbox (Lag de Integración)")
        .register(registry);
}

FASE 4 — Configuración de Prometheus

Archivo: ./prometheus.yml (Raíz)
YAML

global:
  scrape_interval: 10s # Alta resolución para Stock Ledger
  evaluation_interval: 10s

scrape_configs:
  - job_name: 'inventory-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['backend:8081']
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
        replacement: 'production-api'

FASE 5 — Docker Compose (Enterprise Grade)

Acción: Añadir servicios con límites de recursos y healthchecks.
YAML

  prometheus:
    image: prom/prometheus:v2.51.0
    container_name: inventory-prometheus
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: '0.5'
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    networks:
      - inventory-network
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:9090/-/healthy"]

  grafana:
    image: grafana/grafana:10.4.2
    container_name: inventory-grafana
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_USER}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD}
      GF_USERS_ALLOW_SIGN_UP: "false"
      GF_ANALYTICS_REPORTING_ENABLED: "false"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    networks:
      - inventory-network
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:3000/api/health"]

FASE 6 — Provisioning de Grafana (Automation)

Ruta: grafana/provisioning/datasources/prometheus.yml
YAML

apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    url: http://prometheus:9090
    isDefault: true
    editable: false # Previene cambios manuales en producción

FASE 7 — Aislamiento de Tests

Archivo: src/test/resources/application-test.properties
Properties

# Deshabilitar exportación de métricas para evitar overhead en CI/CD
management.metrics.export.prometheus.enabled=false