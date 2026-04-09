
FASE 8: HIBERNATE SECOND-LEVEL CACHE (L2C) CON CAFFEINE

Esta fase resuelve el problema de que los servicios internos (StockLedgerService, OrderService, StockAlertService, WeeklyPlanService, TraceabilityService, KitchenReportService, etc.) llaman directamente a los repositorios JPA (productRepository.findById(), recipeRepository.findById(), etc.), saltándose completamente la caché Redis de @Cacheable. Hibernate L2C cachea las entidades JPA de forma transparente — cada findById desde cualquier servicio pasa por la caché automáticamente, sin modificar ningún servicio.
8.1 Dependencias Maven

Archivo: inventory-service/pom.xml pom.xml:134-137

Añadir dos dependencias junto a la de Caffeine existente (línea 134-137):

    org.hibernate.orm:hibernate-jcache — puente entre Hibernate L2C y la API JCache (JSR-107). No especificar versión, Spring Boot la gestiona.
    com.github.ben-manes.caffeine:jcache — implementación JCache de Caffeine. No especificar versión, Spring Boot la gestiona.

8.2 Configuración en application.properties

Archivo: inventory-service/src/main/resources/application.properties application.properties:32-42

Añadir las siguientes propiedades JPA debajo del bloque existente de spring.jpa.properties.hibernate (después de la línea 42):

    spring.jpa.properties.hibernate.cache.use_second_level_cache=true — activa L2C
    spring.jpa.properties.hibernate.cache.region.factory_class=jcache — usa JCache como factory
    spring.jpa.properties.javax.cache.provider=com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider — usa Caffeine como proveedor JCache
    spring.jpa.properties.hibernate.cache.use_query_cache=true — activa Query Cache (solo para queries de datos maestros)
    spring.jpa.properties.javax.cache.uri=classpath:caffeine-hibernate-cache.xml — archivo de configuración de regiones de caché

NO añadir estas propiedades en application-test.properties — el L2C debe estar desactivado en tests. Añadir en application-test.properties:

    spring.jpa.properties.hibernate.cache.use_second_level_cache=false
    spring.jpa.properties.hibernate.cache.use_query_cache=false application.properties:36-42

8.3 Archivo de configuración de regiones Caffeine

Crear archivo: inventory-service/src/main/resources/caffeine-hibernate-cache.xml

Este archivo define las regiones de caché JCache con configuración específica por entidad. Cada región debe tener:

Entidades de datos maestros (TTL largo, READ_ONLY):

    Región com.economato.inventory.domain.model.Allergen: máximo 200 entradas, expiry 24 horas
    Región com.economato.inventory.domain.model.Supplier: máximo 200 entradas, expiry 12 horas

Entidades de negocio core (TTL medio, READ_WRITE):

    Región com.economato.inventory.domain.model.Product: máximo 1000 entradas, expiry 30 minutos
    Región com.economato.inventory.domain.model.Recipe: máximo 500 entradas, expiry 2 horas
    Región com.economato.inventory.domain.model.RecipeComponent: máximo 2000 entradas, expiry 2 horas
    Región com.economato.inventory.domain.model.User: máximo 500 entradas, expiry 1 hora
    Región com.economato.inventory.domain.model.Order: máximo 500 entradas, expiry 30 minutos
    Región com.economato.inventory.domain.model.OrderDetail: máximo 2000 entradas, expiry 30 minutos
    Región com.economato.inventory.domain.model.ProductBatch: máximo 2000 entradas, expiry 15 minutos

Entidades de planificación (TTL medio):

    Región com.economato.inventory.domain.model.WeeklyPlan: máximo 100 entradas, expiry 30 minutos
    Región com.economato.inventory.domain.model.WeeklyPlanSlot: máximo 1000 entradas, expiry 30 minutos

Entidades de predicción (TTL corto, NONSTRICT_READ_WRITE):

    Región com.economato.inventory.domain.model.StockPrediction: máximo 500 entradas, expiry 10 minutos

Colecciones cacheadas (regiones separadas):

    Región com.economato.inventory.domain.model.Recipe.components: máximo 500 entradas, expiry 2 horas
    Región com.economato.inventory.domain.model.Recipe.allergens: máximo 500 entradas, expiry 2 horas
    Región com.economato.inventory.domain.model.Order.details: máximo 500 entradas, expiry 30 minutos
    Región com.economato.inventory.domain.model.WeeklyPlan.slots: máximo 100 entradas, expiry 30 minutos

Query Cache:

    Región default-query-results-region: máximo 500 entradas, expiry 15 minutos
    Región default-update-timestamps-region: máximo 5000 entradas, expiry 24 horas

NOTA: El formato del XML de configuración JCache de Caffeine usa el esquema de caffeine-jcache. Consultar la documentación de ben-manes/caffeine para el formato exacto del XML. Alternativamente, si el XML resulta problemático, se puede crear un bean javax.cache.CacheManager programático en una clase @Configuration que configure las regiones con MutableConfiguration y CaffeineConfiguration.
8.4 Anotar entidades con @Cache de Hibernate

IMPORTANTE: Usar org.hibernate.annotations.Cache y org.hibernate.annotations.CacheConcurrencyStrategy, NO las anotaciones de JPA @Cacheable de jakarta.persistence.
Entidades de datos maestros — CacheConcurrencyStrategy.READ_ONLY

Allergen (inventory-service/src/main/java/com/economato/inventory/domain/model/Allergen.java): Allergen.java:26-40

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY) a nivel de clase, justo debajo de @Entity
    NO cachear la colección recipes (línea 38-40) — es el lado inverso del ManyToMany, se accede raramente y es potencialmente grande

Supplier (inventory-service/src/main/java/com/economato/inventory/domain/model/Supplier.java): Supplier.java:20-39

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY) a nivel de clase
    NO cachear la colección products (línea 38-39) — es el lado inverso, potencialmente grande

NOTA sobre READ_ONLY: Si en algún momento se modifican alérgenos o proveedores (save/update/delete), Hibernate lanzará una excepción al intentar actualizar una entidad READ_ONLY en L2C. Si los servicios AllergenService y SupplierService tienen operaciones de escritura (que las tienen), usar NONSTRICT_READ_WRITE en lugar de READ_ONLY para evitar excepciones. Verificar si las escrituras son frecuentes — si son raras (datos maestros), NONSTRICT_READ_WRITE es la opción segura.
Entidades de negocio core — CacheConcurrencyStrategy.READ_WRITE

Product (inventory-service/src/main/java/com/economato/inventory/domain/model/Product.java): Product.java:21-77

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase
    NO cachear la colección orderDetails (línea 76-77) — lado inverso, se accede raramente
    La entidad tiene @Version (línea 71-73), lo cual es compatible con READ_WRITE y ayuda a la consistencia del L2C

Recipe (inventory-service/src/main/java/com/economato/inventory/domain/model/Recipe.java): Recipe.java:23-81

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase
    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a la colección components (línea 63-65) — se accede frecuentemente desde múltiples servicios
    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a la colección allergens (línea 77-81) — se accede frecuentemente para mostrar alérgenos de recetas

RecipeComponent (inventory-service/src/main/java/com/economato/inventory/domain/model/RecipeComponent.java): RecipeComponent.java:26-47

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase

User (inventory-service/src/main/java/com/economato/inventory/domain/model/User.java): User.java:20-62

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase
    NO cachear las colecciones orders (línea 53-54) ni inventoryMovements (línea 57-58) — lados inversos, potencialmente grandes

Order (inventory-service/src/main/java/com/economato/inventory/domain/model/Order.java): Order.java:23-56

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase
    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a la colección details (línea 55-56) — se accede frecuentemente al cargar pedidos

OrderDetail — Buscar la entidad OrderDetail.java y añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase

ProductBatch (inventory-service/src/main/java/com/economato/inventory/domain/model/ProductBatch.java): ProductBatch.java:38-78

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase

Entidades de planificación — CacheConcurrencyStrategy.READ_WRITE

WeeklyPlan (inventory-service/src/main/java/com/economato/inventory/domain/model/WeeklyPlan.java): WeeklyPlan.java:32-67

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase
    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a la colección slots (línea 65-67)

WeeklyPlanSlot — Buscar la entidad WeeklyPlanSlot.java y añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) a nivel de clase. Si tiene una colección de WeeklyPlanSlotStudent, cachear esa colección también.
Entidades de predicción — CacheConcurrencyStrategy.NONSTRICT_READ_WRITE

StockPrediction (inventory-service/src/main/java/com/economato/inventory/domain/model/StockPrediction.java): StockPrediction.java:20-42

    Añadir @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE) a nivel de clase
    Se usa NONSTRICT_READ_WRITE porque se actualiza de forma asíncrona desde Kafka y no necesita locks estrictos

Entidades que NO se deben cachear (append-only, audit, transient):

    StockLedger — ledger inmutable, append-only, nunca se lee por ID desde servicios
    StockLedgerBatchDetail — append-only
    StockSnapshot — se actualiza con cada movimiento de stock, alta volatilidad
    InventoryAudit — log de auditoría, append-only
    RecipeAudit — log de auditoría
    OrderAudit — log de auditoría
    RecipeCookingAudit — log de auditoría
    AuditOutbox — outbox transaccional, se borra tras procesar
    RevokedToken — tokens revocados, corta vida
    Notification — transient
    FoodCrisis, CrisisAffectedProduct — muy raros
    TemporaryRoleEscalation — muy raro
    StockDailyForecast — se lee por queries custom, no por ID
    StockWeeklyConsumptionHistory — se lee por queries custom

8.5 Query Cache selectivo para datos maestros

Habilitar Query Cache solo en los repositorios de datos maestros que cambian raramente. Añadir @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true")) a los siguientes métodos de repositorio:

    AllergenRepository.findAll() — los alérgenos son pocos y casi nunca cambian
    SupplierRepository.findAll() — los proveedores son pocos y cambian raramente

NO habilitar Query Cache en repositorios de entidades volátiles (ProductRepository, OrderRepository, etc.) — el Query Cache se invalida por tabla completa, no por entidad, y causaría más invalidaciones que beneficios.
8.6 Verificación y métricas

Añadir la siguiente propiedad en application.properties para poder verificar que el L2C funciona:

    spring.jpa.properties.hibernate.generate_statistics=true — temporalmente durante desarrollo/testing para ver hit/miss ratios en logs

Verificar en los logs de Hibernate que aparecen estadísticas de L2C con hits. Una vez verificado, desactivar generate_statistics en producción (genera overhead) y confiar en las métricas de Micrometer/Prometheus que ya están configuradas.
8.7 Resumen de archivos a modificar en esta fase

    inventory-service/pom.xml — Añadir 2 dependencias (hibernate-jcache, caffeine:jcache)
    inventory-service/src/main/resources/application.properties — Añadir 5 propiedades de Hibernate L2C
    inventory-service/src/main/resources/application-test.properties — Desactivar L2C en tests
    inventory-service/src/main/resources/caffeine-hibernate-cache.xml — CREAR archivo de configuración de regiones (o alternativamente, configurar programáticamente)
    Allergen.java — Añadir @Cache(NONSTRICT_READ_WRITE)
    Supplier.java — Añadir @Cache(NONSTRICT_READ_WRITE)
    Product.java — Añadir @Cache(READ_WRITE)
    Recipe.java — Añadir @Cache(READ_WRITE) a clase + colecciones components y allergens
    RecipeComponent.java — Añadir @Cache(READ_WRITE)
    User.java — Añadir @Cache(READ_WRITE)
    Order.java — Añadir @Cache(READ_WRITE) a clase + colección details
    OrderDetail.java — Añadir @Cache(READ_WRITE)
    ProductBatch.java — Añadir @Cache(READ_WRITE)
    WeeklyPlan.java — Añadir @Cache(READ_WRITE) a clase + colección slots
    WeeklyPlanSlot.java — Añadir @Cache(READ_WRITE) a clase (+ colección de students si existe)
    StockPrediction.java — Añadir @Cache(NONSTRICT_READ_WRITE)
    AllergenRepository.java — Añadir @QueryHints cacheable a findAll()
    SupplierRepository.java — Añadir @QueryHints cacheable a findAll()
