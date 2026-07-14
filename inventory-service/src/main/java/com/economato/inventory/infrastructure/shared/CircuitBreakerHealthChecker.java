package com.economato.inventory.infrastructure.shared;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.errors.NetworkException;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Revisa proactivamente la salud de las dependencias críticas (DB, Redis, Kafka) y abre los circuit breakers inmediatamente al detectar fallos.
 * También revisa periódicamente la recuperación de estas dependencias para cerrar los circuit breakers.
 * Este enfoque agresivo de health check permite detectar y reaccionar a fallos en segundos, minimizando el impacto en los usuarios.
 */
@Slf4j
@Service
@Profile("!test & !resilience-test")
public class CircuitBreakerHealthChecker {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final DataSource writerDataSource;
    private final DataSource readerDataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final KafkaTemplate<String, ?> kafkaTemplate;

    public CircuitBreakerHealthChecker(
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Qualifier("writerDataSource") DataSource writerDataSource,
            @Qualifier("readerDataSource") DataSource readerDataSource,
            RedisConnectionFactory redisConnectionFactory,
            @Qualifier("inventoryAuditKafkaTemplate") KafkaTemplate<String, ?> kafkaTemplate) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.writerDataSource = writerDataSource;
        this.readerDataSource = readerDataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.kafkaTemplate = kafkaTemplate;
    }


    @Scheduled(fixedDelay = 3000)
    public void proactiveDbHealthCheck() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        
        if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return;
        }

        boolean writerHealthy = testDatabaseConnection(writerDataSource, "WRITER", 2);

        if (!writerHealthy) {
            log.warn("PRIMARY DATABASE (WRITER) IS DOWN! Opening circuit breaker immediately");
            RuntimeException error = new JDBCConnectionException(
                    "Writer database connection failed",
                    new SQLException("Health check failed")
            );
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, error);
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void proactiveReplicaHealthCheck() {
        CircuitBreaker replicaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("replica");
        
        if (replicaCircuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return;
        }

        boolean readerHealthy = testDatabaseConnection(readerDataSource, "READER", 2);

        if (!readerHealthy) {
            log.warn("REPLICA DATABASE (READER) IS DOWN! Opening replica circuit breaker to alert frontend");
            RuntimeException error = new JDBCConnectionException(
                    "Reader (replica) database connection failed",
                    new SQLException("Health check failed")
            );
            replicaCircuitBreaker.onError(0, TimeUnit.MILLISECONDS, error);
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void checkDatabaseRecovery() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("db");

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            boolean writerHealthy = testDatabaseConnection(writerDataSource, "WRITER", 5);

            if (writerHealthy) {
                log.info("PRIMARY DATABASE (WRITER) recovered, closing circuit breaker");
                circuitBreaker.transitionToClosedState();
            } else {
                log.debug("Primary database still unavailable");
            }
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void checkReplicaRecovery() {
        CircuitBreaker replicaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("replica");

        if (replicaCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            boolean readerHealthy = testDatabaseConnection(readerDataSource, "READER", 5);

            if (readerHealthy) {
                log.info("REPLICA DATABASE (READER) recovered, closing circuit breaker");
                replicaCircuitBreaker.transitionToClosedState();
            } else {
                log.debug("Replica database still unavailable");
            }
        }
    }

    private boolean testDatabaseConnection(DataSource dataSource, String name, int timeoutSeconds) {
        try (Connection conn = dataSource.getConnection()) {
            boolean isValid = conn.isValid(timeoutSeconds);
            if (isValid) {
                log.debug("{} database is healthy", name);
            } else {
                log.warn("{} database - isValid() returned false", name);
            }
            return isValid;
        } catch (Exception e) {
            log.warn("{} database connection failed: {}", name, e.getMessage());
            return false;
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void proactiveRedisHealthCheck() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        
        if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return;
        }

        if (!testRedisConnection()) {
            log.warn("Redis is DOWN! Opening circuit breaker immediately");
            RuntimeException error = new RedisConnectionFailureException(
                    "Redis connection failed"
            );
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, error);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void checkRedisRecovery() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            if (testRedisConnection()) {
                log.info("Redis recovered, closing circuit breaker");
                circuitBreaker.transitionToClosedState();
            } else {
                log.debug("Redis still unavailable");
            }
        }
    }

    private boolean testRedisConnection() {
        try {
            var conn = redisConnectionFactory.getConnection();
            try {
                var response = conn.ping();
                log.debug("Redis ping response: {}", response);
                return true;
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            log.warn("Redis connection failed: {}", e.getMessage());
            return false;
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void proactiveKafkaHealthCheck() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
        
        if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return;
        }

        if (!testKafkaConnection()) {
            log.warn("Kafka is DOWN! Opening circuit breaker immediately");
            RuntimeException error = new NetworkException(
                    "Kafka connection failed"
            );
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, error);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void checkKafkaRecovery() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            if (testKafkaConnection()) {
                log.info("Kafka recovered, closing circuit breaker");
                circuitBreaker.transitionToClosedState();
            } else {
                log.debug("Kafka still unavailable");
            }
        }
    }

    private boolean testKafkaConnection() {
        AdminClient adminClient = null;
        try {
            ProducerFactory<String, ?> producerFactory = kafkaTemplate.getProducerFactory();
            Map<String, Object> adminConfigs = new HashMap<>(producerFactory.getConfigurationProperties());
            adminConfigs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            adminConfigs.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 9000);

            adminClient = AdminClient.create(adminConfigs);
            adminClient.describeCluster().clusterId().get(5, TimeUnit.SECONDS);

            return true;
        } catch (Exception e) {
            log.warn("Kafka connection failed: {}", e.getMessage());
            return false;
        } finally {
            if (adminClient != null) {
                try {
                    adminClient.close(Duration.ofSeconds(5));
                } catch (Exception e) {
                    log.debug("Error closing Kafka admin client: {}", e.getMessage());
                }
            }
        }
    }
}
