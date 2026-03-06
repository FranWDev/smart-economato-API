package com.economato.inventory;

import com.economato.inventory.kafka.producer.AuditOutboxProcessor;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.security.JwtUtils;
import com.economato.inventory.service.notification.AlertMessage;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.hibernate.exception.JDBCConnectionException;
import java.sql.SQLException;
import java.net.UnknownHostException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.NetworkException;
import com.economato.inventory.service.CustomUserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.economato.inventory.health.CircuitBreakerHealthChecker;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class CircuitBreakerIntegrationTest {

        @Autowired
        private CircuitBreakerRegistry registry;

        @MockitoBean
        private ProductRepository productRepository;

        @MockitoBean
        private SimpMessagingTemplate messagingTemplate;

        @MockitoBean
        private AuditOutboxProcessor outboxProcessor;

        @MockitoBean
        private JwtUtils jwtUtils;

        @MockitoBean
        private CustomUserDetailsService userDetailsService;

        @BeforeEach
        void setUp() {
                registry.circuitBreaker("db").transitionToClosedState();
                registry.circuitBreaker("replica").transitionToClosedState();
                registry.circuitBreaker("redis").transitionToClosedState();
                registry.circuitBreaker("kafka").transitionToClosedState();
                reset(messagingTemplate);
        }

        @Test
        void testDbCircuitBreakerOpensAndSendsAlert() {
                CircuitBreaker dbCb = registry.circuitBreaker("db");
                RuntimeException fakeException = new JDBCConnectionException(
                                "DB Connection Refused",
                                new SQLException());

                dbCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

                assert (dbCb.getState() == CircuitBreaker.State.OPEN);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "DB_FAILURE".equals(msg.getCode())));
        }

        @Test
        void testRedisCircuitBreakerOpensAndSendsPartialAlert() {
                CircuitBreaker redisCb = registry.circuitBreaker("redis");
                RuntimeException fakeException = new RedisConnectionFailureException("Redis is down");

                redisCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

                assert (redisCb.getState() == CircuitBreaker.State.OPEN);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "REDIS_FAILURE".equals(msg.getCode())));
        }

        @Test
        void testKafkaCircuitBreakerOpensAndSendsPartialAlert() {
                CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");
                RuntimeException fakeException = new TimeoutException(
                                "Kafka broker unreachable");

                kafkaCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

                assert (kafkaCb.getState() == CircuitBreaker.State.OPEN);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "KAFKA_FAILURE".equals(msg.getCode())));
        }

        @Test
        void testDbCircuitBreakerRecoveryAndSendsAlert() {
                CircuitBreaker dbCb = registry.circuitBreaker("db");

                RuntimeException fakeException = new JDBCConnectionException(
                                "DB Connection Refused",
                                new SQLException());
                dbCb.onError(0, TimeUnit.MILLISECONDS, fakeException);
                assert (dbCb.getState() == CircuitBreaker.State.OPEN);

                reset(messagingTemplate);

                dbCb.transitionToClosedState();
                assert (dbCb.getState() == CircuitBreaker.State.CLOSED);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "DB_RECOVERED".equals(msg.getCode())));
        }

        @Test
        void testRedisCircuitBreakerRecoveryAndSendsAlert() {
                CircuitBreaker redisCb = registry.circuitBreaker("redis");

                RuntimeException fakeException = new RedisConnectionFailureException("Redis is down");
                redisCb.onError(0, TimeUnit.MILLISECONDS, fakeException);
                assert (redisCb.getState() == CircuitBreaker.State.OPEN);

                reset(messagingTemplate);

                redisCb.transitionToClosedState();
                assert (redisCb.getState() == CircuitBreaker.State.CLOSED);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "REDIS_RECOVERED".equals(msg.getCode())));
        }

        @Test
        void testKafkaCircuitBreakerRecoveryAndSendsAlert() {
                CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");

                RuntimeException fakeException = new TimeoutException(
                                "Kafka broker unreachable");
                kafkaCb.onError(0, TimeUnit.MILLISECONDS, fakeException);
                assert (kafkaCb.getState() == CircuitBreaker.State.OPEN);

                reset(messagingTemplate);

                kafkaCb.transitionToClosedState();
                assert (kafkaCb.getState() == CircuitBreaker.State.CLOSED);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "KAFKA_RECOVERED".equals(msg.getCode())));
        }

        @Test
        void testDbCircuitBreakerOpensOnUnknownHostException() {
                CircuitBreaker dbCb = registry.circuitBreaker("db");
                RuntimeException fakeException = new JDBCConnectionException(
                                "Cannot resolve host postgres-replica",
                                new SQLException(new UnknownHostException("postgres-replica")));

                dbCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

                assert (dbCb.getState() == CircuitBreaker.State.OPEN);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "DB_FAILURE".equals(msg.getCode())));
        }

        @Test
        void testRedisCircuitBreakerOpensOnUnknownHostException() {
                CircuitBreaker redisCb = registry.circuitBreaker("redis");
                RuntimeException fakeException = new RedisConnectionFailureException(
                                "Cannot resolve host redis",
                                new UnknownHostException("redis"));

                redisCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

                assert (redisCb.getState() == CircuitBreaker.State.OPEN);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "REDIS_FAILURE".equals(msg.getCode())));
        }

        @Test
        void testReplicaCircuitBreakerOpensAndSendsAlert() {
                CircuitBreaker replicaCb = registry.circuitBreaker("replica");
                RuntimeException fakeException = new JDBCConnectionException(
                                "Replica Connection Refused",
                                new SQLException());

                replicaCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

                assert (replicaCb.getState() == CircuitBreaker.State.OPEN);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "REPLICA_FAILURE".equals(msg.getCode())));
        }

        @Test
        void testReplicaCircuitBreakerRecoveryAndSendsAlert() {
                CircuitBreaker replicaCb = registry.circuitBreaker("replica");

                RuntimeException fakeException = new JDBCConnectionException(
                                "Replica Connection Refused",
                                new SQLException());
                replicaCb.onError(0, TimeUnit.MILLISECONDS, fakeException);
                assert (replicaCb.getState() == CircuitBreaker.State.OPEN);

                reset(messagingTemplate);

                replicaCb.transitionToClosedState();
                assert (replicaCb.getState() == CircuitBreaker.State.CLOSED);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "REPLICA_RECOVERED".equals(msg.getCode())));
        }

        @Test
        void testReplicaCircuitBreakerOpensOnUnknownHostException() {
                CircuitBreaker replicaCb = registry.circuitBreaker("replica");
                RuntimeException fakeException = new JDBCConnectionException(
                                "Cannot resolve host postgres-replica",
                                new SQLException(new UnknownHostException("postgres-replica")));

                replicaCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

                assert (replicaCb.getState() == CircuitBreaker.State.OPEN);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "REPLICA_FAILURE".equals(msg.getCode())));
        }

        @Test
        void testKafkaCircuitBreakerOpensOnUnknownHostException() {
                CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");
                RuntimeException fakeException = new NetworkException(
                                "Cannot resolve host kafka",
                                new UnknownHostException("kafka"));

                kafkaCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

                assert (kafkaCb.getState() == CircuitBreaker.State.OPEN);

                verify(messagingTemplate, atLeastOnce()).convertAndSend(
                                eq("/topic/alerts"),
                                argThat((AlertMessage msg) -> "KAFKA_FAILURE".equals(msg.getCode())));
        }
}
