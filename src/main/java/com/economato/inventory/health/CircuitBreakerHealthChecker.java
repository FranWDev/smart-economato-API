package com.economato.inventory.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Proactively monitors database, Redis, and Kafka health.
 * When a circuit breaker is OPEN, it periodically tests recovery.
 * Also performs aggressive upfront health checks to detect failures early.
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

    /**
     * Aggressive proactive health check - runs every 3 seconds
     * Detects failures early and opens circuit breaker immediately
     * Only opens the circuit breaker if WRITER (primary) fails.
     */
    @Scheduled(fixedDelay = 3000)
    public void proactiveDbHealthCheck() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        
        // Only perform proactive checks if circuit is still CLOSED
        // to avoid hammering dead services
        if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return;
        }

        boolean writerHealthy = testDatabaseConnection(writerDataSource, "WRITER", 2);

        // ONLY open if WRITER (primary) is down
        if (!writerHealthy) {
            log.warn("PRIMARY DATABASE (WRITER) IS DOWN! Opening circuit breaker immediately");
            RuntimeException error = new org.hibernate.exception.JDBCConnectionException(
                    "Writer database connection failed",
                    new java.sql.SQLException("Health check failed")
            );
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, error);
        }
    }

    /**
     * Proactive health check for Read Replica - runs every 3 seconds
     * Opens replica circuit breaker when READER (replica) is down to alert frontend
     */
    @Scheduled(fixedDelay = 3000)
    public void proactiveReplicaHealthCheck() {
        CircuitBreaker replicaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("replica");
        
        // Only perform proactive checks if circuit is still CLOSED
        if (replicaCircuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return;
        }

        boolean readerHealthy = testDatabaseConnection(readerDataSource, "READER", 2);

        if (!readerHealthy) {
            log.warn("REPLICA DATABASE (READER) IS DOWN! Opening replica circuit breaker to alert frontend");
            RuntimeException error = new org.hibernate.exception.JDBCConnectionException(
                    "Reader (replica) database connection failed",
                    new java.sql.SQLException("Health check failed")
            );
            replicaCircuitBreaker.onError(0, TimeUnit.MILLISECONDS, error);
        }
    }

    /**
     * Recovery check - runs every 10 seconds
     * Only closes the circuit breaker when database is healthy
     */
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

    /**
     * Recovery check for Read Replica - runs every 10 seconds
     * Closes replica circuit breaker when reader recovers
     */
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

    /**
     * Proactive Redis health check - every 5 seconds
     */
    @Scheduled(fixedDelay = 5000)
    public void proactiveRedisHealthCheck() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        
        if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return;
        }

        if (!testRedisConnection()) {
            log.warn("Redis is DOWN! Opening circuit breaker immediately");
            RuntimeException error = new org.springframework.data.redis.RedisConnectionFailureException(
                    "Redis connection failed"
            );
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, error);
        }
    }

    /**
     * Recovery check for Redis - every 15 seconds
     */
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

    /**
     * Proactive Kafka health check - every 5 seconds
     */
    @Scheduled(fixedDelay = 5000)
    public void proactiveKafkaHealthCheck() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
        
        if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return;
        }

        if (!testKafkaConnection()) {
            log.warn("Kafka is DOWN! Opening circuit breaker immediately");
            RuntimeException error = new org.apache.kafka.common.errors.NetworkException(
                    "Kafka connection failed"
            );
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, error);
        }
    }

    /**
     * Recovery check for Kafka - every 15 seconds
     */
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
