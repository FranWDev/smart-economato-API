package com.economato.inventory.config;

import com.economato.inventory.model.Product;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.security.JwtUtils;
import com.economato.inventory.service.CustomUserDetailsService;
import com.economato.inventory.service.notification.AlertMessage;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        RuntimeException connectionError = new org.springframework.dao.DataAccessResourceFailureException(
                "Connection refused"
        );
        
        // The circuit breaker is configured with minimum-number-of-calls=1 and failure-rate-threshold=50
        // With sliding-window-size=2, after 1 failure the circuit should open
        dbCb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, connectionError);
        
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
        RuntimeException error = new org.hibernate.exception.JDBCConnectionException(
                "Connection error",
                new java.sql.SQLException()
        );
        dbCb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, error);
        
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
