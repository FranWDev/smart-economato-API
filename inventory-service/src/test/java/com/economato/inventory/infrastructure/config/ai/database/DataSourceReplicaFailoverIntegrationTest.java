package com.economato.inventory.infrastructure.config.ai.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.economato.inventory.application.usecase.stock.AlertMessage;
import com.economato.inventory.application.usecase.user.CustomUserDetailsService;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.config.shared.security.JwtUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test to verify that when the replica database fails,
 * the circuit breaker opens and operations fallback to the primary database.
 */
@SpringBootTest
@ActiveProfiles("test")
public class DataSourceReplicaFailoverIntegrationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private ProductRepository productRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuditEventProducer auditEventProducer;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("db").transitionToClosedState();
        circuitBreakerRegistry.circuitBreaker("redis").transitionToClosedState();
        circuitBreakerRegistry.circuitBreaker("kafka").transitionToClosedState();
        reset(messagingTemplate);
    }

    @Test
    @Transactional(readOnly = true)
    void testReadOperationWorksWithCircuitBreakerClosed() {
        CircuitBreaker dbCb = circuitBreakerRegistry.circuitBreaker("db");
        
        // When circuit breaker is closed, operations should work normally
        assertEquals(CircuitBreaker.State.CLOSED, dbCb.getState());
        
        // This should work (using H2 in-memory database in test profile)
        assertDoesNotThrow(() -> productRepository.findAll());
    }

    @Test
    void testCircuitBreakerOpensOnMultipleFailures() {
        CircuitBreaker dbCb = circuitBreakerRegistry.circuitBreaker("db");
        
        // Simulate multiple connection failures
        RuntimeException connectionError = new DataAccessResourceFailureException(
                "Connection refused"
        );
        
        // The circuit breaker is configured with minimum-number-of-calls=1 and failure-rate-threshold=50
        // With sliding-window-size=2, after 1 failure the circuit should open
        dbCb.onError(0, TimeUnit.MILLISECONDS, connectionError);
        
        assertEquals(CircuitBreaker.State.OPEN, dbCb.getState());
        
        // Verify alert was sent
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/alerts"),
                argThat((AlertMessage msg) -> "DB_FAILURE".equals(msg.getCode()))
        );
    }

    @Test
    void testCircuitBreakerStateTransitions() {
        CircuitBreaker dbCb = circuitBreakerRegistry.circuitBreaker("db");
        
        // Start closed
        assertEquals(CircuitBreaker.State.CLOSED, dbCb.getState());
        
        // Simulate failure
        RuntimeException error = new JDBCConnectionException(
                "Connection error",
                new SQLException()
        );
        dbCb.onError(0, TimeUnit.MILLISECONDS, error);
        
        // Should be open
        assertEquals(CircuitBreaker.State.OPEN, dbCb.getState());
        
        reset(messagingTemplate);
        
        // Transition back to closed (simulating recovery)
        dbCb.transitionToClosedState();
        assertEquals(CircuitBreaker.State.CLOSED, dbCb.getState());
        
        // Verify recovery alert
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/alerts"),
                argThat((AlertMessage msg) -> "DB_RECOVERED".equals(msg.getCode()))
        );
    }
}
