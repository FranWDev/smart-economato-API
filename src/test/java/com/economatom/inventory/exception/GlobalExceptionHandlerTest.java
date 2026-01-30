package com.economatom.inventory.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import io.jsonwebtoken.JwtException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Test
    void handleResourceNotFoundException_ShouldReturnNotFound() {
        // Arrange
        ResourceNotFoundException exception = new ResourceNotFoundException("Resource not found");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Resource not found", response.getBody().getMessage());
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    void handleInsufficientStockException_ShouldReturnBadRequest() {
        // Arrange
        InsufficientStockException exception = new InsufficientStockException("Insufficient stock");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientStockException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Insufficient stock", response.getBody().getMessage());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleInvalidOperationException_ShouldReturnBadRequest() {
        // Arrange
        InvalidOperationException exception = new InvalidOperationException("Invalid operation");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidOperationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid operation", response.getBody().getMessage());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleMethodArgumentNotValidException_ShouldReturnValidationErrors() {
        // Arrange
        FieldError fieldError = new FieldError("object", "field", "Field error message");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getAllErrors()).thenReturn(java.util.Arrays.asList(fieldError));
        
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        // Act
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("field"));
        assertEquals("Field error message", response.getBody().get("field"));
    }

    @Test
    void handleConstraintViolationException_ShouldReturnConstraintErrors() {
        // Arrange
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("propertyName");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("Constraint violation message");
        violations.add(violation);
        
        ConstraintViolationException exception = new ConstraintViolationException(violations);

        // Act
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleConstraintViolationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("propertyName"));
        assertEquals("Constraint violation message", response.getBody().get("propertyName"));
    }

    @Test
    void handleOptimisticLockException_ShouldReturnConflict() {
        // Arrange
        OptimisticLockException exception = new OptimisticLockException("Optimistic lock");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleOptimisticLockException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("modificados por otro usuario"));
    }

    @Test
    void handleOptimisticLockingFailureException_ShouldReturnConflict() {
        // Arrange
        OptimisticLockingFailureException exception = new OptimisticLockingFailureException("Optimistic locking failure");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleOptimisticLockingFailureException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("modificados por otro usuario"));
    }

    @Test
    void handlePessimisticLockingFailureException_ShouldReturnLocked() {
        // Arrange
        PessimisticLockingFailureException exception = new PessimisticLockingFailureException("Pessimistic locking failure");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handlePessimisticLockingFailureException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.LOCKED, response.getStatusCode());
        assertEquals(423, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("siendo usado"));
    }

    @Test
    void handleConcurrencyException_ShouldReturnConflict() {
        // Arrange
        ConcurrencyException exception = new ConcurrencyException("Product", 1);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleConcurrencyException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("Product"));
    }

    @Test
    void handleStockLockException_ShouldReturnLocked() {
        // Arrange
        StockLockException exception = new StockLockException("Stock lock error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleStockLockException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.LOCKED, response.getStatusCode());
        assertEquals(423, response.getBody().getStatus());
    }

    @Test
    void handleBadCredentials_ShouldReturnUnauthorized() {
        // Arrange
        BadCredentialsException exception = new BadCredentialsException("Bad credentials");

        // Act
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBadCredentials(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("incorrectos"));
    }

    @Test
    void handleJwtException_ShouldReturnUnauthorized() {
        // Arrange
        JwtException exception = new JwtException("JWT error");

        // Act
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleJwtException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("JWT"));
    }

    @Test
    void handleGeneralException_ShouldReturnInternalServerError() {
        // Arrange
        Exception exception = new Exception("Generic error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGeneralException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
    }
}
