# Guía de Testing - Sistema de Inventario

## Configuración de Tests

### Tests sin Kafka

Los tests están configurados para ejecutarse **sin necesidad de levantar Kafka**. La configuración de Kafka está deshabilitada automáticamente en el perfil `test`.

#### Configuración Aplicada:

1. **KafkaConfig.java**: Marcado con `@Profile("!test")` - No se carga en tests
2. **application-test.properties**: Configurado para minimizar logs de Kafka
3. **KafkaTestConfig.java**: Clase vacía que previene la inicialización de Kafka en tests

### Ejecutar Tests

#### Opción 1: Ejecutar todos los tests con Maven
```bash
./mvnw test
```

#### Opción 2: Ejecutar tests de una clase específica
```bash
./mvnw test -Dtest=UserServiceTest
```

#### Opción 3: Ejecutar un test específico
```bash
./mvnw test -Dtest=UserServiceTest#findById_WhenUserExists_ShouldReturnUser
```

#### Opción 4: Ejecutar tests desde IDE
- IntelliJ IDEA: Clic derecho en la clase de test → "Run"
- Eclipse: Clic derecho → "Run As" → "JUnit Test"

### Ejecutar con Cobertura

```bash
./mvnw clean test jacoco:report
```

El reporte se genera en: `target/site/jacoco/index.html`

## Tests con Kafka (Opcional)

Si necesitas ejecutar tests que validen la integración con Kafka:

### Opción 1: Usar Testcontainers (Recomendado)

Añade la dependencia en `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
```

Ejemplo de test con Kafka embebido:
```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class KafkaIntegrationTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:latest")
    );
    
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    // Tests aquí...
}
```

### Opción 2: Levantar Kafka con Docker Compose

```bash
docker-compose up -d kafka
./mvnw test -Dspring.profiles.active=integration
```

**docker-compose.yml**:
```yaml
services:
  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - "9093:9093"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
  
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
```

## Estructura de Tests

### Tests Unitarios de Servicios
- `UserServiceTest.java` - 14 tests
- `ProductServiceTest.java` - 17 tests
- `OrderServiceTest.java` - 15 tests
- `RecipeServiceTest.java` - 13 tests
- `AllergenServiceTest.java` - 9 tests
- `OrderDetailServiceTest.java` - 3 tests

### Tests de Excepciones
- `GlobalExceptionHandlerTest.java` - 13 tests

### Tests de Integración
- `UserControllerIntegrationTest.java`
- `ProductControllerIntegrationTest.java`
- `OrderControllerIntegrationTest.java`
- `RecipeControllerIntegrationTest.java`
- `AllergenControllerIntegrationTest.java`
- `AuthControllerIntegrationTest.java`

### Tests de Casos Edge
- `ProductControllerEdgeCasesTest.java` - 12 tests
- `AuthControllerEdgeCasesTest.java` - 11 tests

## Cobertura Esperada

Con los tests implementados, la cobertura debería ser:
- **Servicios**: ~90-95%
- **Controladores**: ~80-85%
- **Excepciones**: ~95-100%
- **Mappers**: ~70-80%
- **Modelos**: ~60-70%

## Solución de Problemas

### Problema: Logs de Kafka en tests
**Síntoma**: Warnings de conexión a Kafka durante tests
**Solución**: Los logs están minimizados en `application-test.properties` con nivel ERROR

### Problema: H2 no compatible con PostgreSQL
**Síntoma**: Errores SQL durante tests
**Solución**: Se usa `MODE=PostgreSQL` en la URL de H2

### Problema: Transacciones no hacen rollback
**Síntoma**: Datos persisten entre tests
**Solución**: Usar `clearDatabase()` en `@BeforeEach` (ya implementado en `BaseIntegrationTest`)

### Problema: JWT inválido en tests
**Síntoma**: Tests de integración fallan con 401
**Solución**: Verificar que el token se genera correctamente en el método de login

## Mejores Prácticas

1. ✅ **Usar `@ActiveProfiles("test")`** en todas las clases de test
2. ✅ **Limpiar la BD antes de cada test** con `clearDatabase()`
3. ✅ **Mockear dependencias externas** (Kafka, Redis) en tests unitarios
4. ✅ **Usar H2 en modo PostgreSQL** para tests de integración
5. ✅ **Separar tests unitarios de integración**
6. ✅ **Nombrar tests descriptivamente**: `metodo_Escenario_ResultadoEsperado`

## Recursos Adicionales

- [Spring Boot Testing](https://spring.io/guides/gs/testing-web/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Testcontainers](https://www.testcontainers.org/)
