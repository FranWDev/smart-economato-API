package com.economato.inventory;

import com.economato.inventory.kafka.producer.AuditOutboxProcessor;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.security.JwtUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.economato.inventory.service.CustomUserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
        LocaleContextHolder.setLocale(Locale.of("es", "ES"));

        registry.circuitBreaker("db").transitionToClosedState();
        registry.circuitBreaker("redis").transitionToClosedState();
        registry.circuitBreaker("kafka").transitionToClosedState();
        reset(messagingTemplate);
    }

    @Test
    void testDbCircuitBreakerOpensAndSendsAlert() {
        CircuitBreaker dbCb = registry.circuitBreaker("db");
        RuntimeException fakeException = new org.hibernate.exception.JDBCConnectionException("DB Connection Refused",
                new java.sql.SQLException());

        dbCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

        assert (dbCb.getState() == CircuitBreaker.State.OPEN);

        String expectedMessage = "Base de datos caída. Sistema no disponible. Contacte con servicio técnico.";
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/alerts"), eq(expectedMessage));
    }

    @Test
    void testRedisCircuitBreakerOpensAndSendsPartialAlert() {
        CircuitBreaker redisCb = registry.circuitBreaker("redis");
        RuntimeException fakeException = new RedisConnectionFailureException("Redis is down");

        redisCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

        assert (redisCb.getState() == CircuitBreaker.State.OPEN);

        String expectedMessage = "Sistema caído parcialmente pero es aún operativo, notifica al servicio técnico.";
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/alerts"), eq(expectedMessage));
    }

    @Test
    void testKafkaCircuitBreakerOpensAndSendsPartialAlert() {
        CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");
        RuntimeException fakeException = new org.apache.kafka.common.errors.TimeoutException(
                "Kafka broker unreachable");

        kafkaCb.onError(0, TimeUnit.MILLISECONDS, fakeException);

        assert (kafkaCb.getState() == CircuitBreaker.State.OPEN);

        String expectedMessage = "Sistema caído parcialmente pero es aún operativo, notifica al servicio técnico.";
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/alerts"), eq(expectedMessage));
    }
}
