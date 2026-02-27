package com.economato.inventory.exception;

import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.i18n.MessageKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Test
    void handleResourceNotFoundException_ShouldReturnNotFound() {

        ResourceNotFoundException exception = new ResourceNotFoundException("Resource not found");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Resource not found", response.getBody().getMessage());
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    void handleInsufficientStockException_ShouldReturnBadRequest() {

        InsufficientStockException exception = new InsufficientStockException("Insufficient stock");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientStockException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Insufficient stock", response.getBody().getMessage());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleInvalidOperationException_ShouldReturnBadRequest() {

        InvalidOperationException exception = new InvalidOperationException("Invalid operation");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidOperationException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid operation", response.getBody().getMessage());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleMethodArgumentNotValidException_ShouldReturnValidationErrors() {

        FieldError fieldError = new FieldError("object", "field", "Field error message");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getAllErrors()).thenReturn(java.util.Arrays.asList(fieldError));

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, String>> response = exceptionHandler.handleValidationExceptions(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("field"));
        assertEquals("Field error message", response.getBody().get("field"));
    }

    @Test
    void handleConstraintViolationException_ShouldReturnConstraintErrors() {

        Set<ConstraintViolation<?>> violations = new HashSet<>();

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("propertyName");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("Constraint violation message");
        violations.add(violation);

        ConstraintViolationException exception = new ConstraintViolationException(violations);

        ResponseEntity<Map<String, String>> response = exceptionHandler.handleConstraintViolationException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("propertyName"));
        assertEquals("Constraint violation message", response.getBody().get("propertyName"));
    }

    @Test
    void handleOptimisticLockException_ShouldReturnConflict() {
        when(i18nService.getMessage(MessageKey.ERROR_OPTIMISTIC_LOCK))
                .thenReturn(
                        "Los datos fueron modificados por otro usuario. Por favor, recarga la página e intenta nuevamente.");

        OptimisticLockException exception = new OptimisticLockException("Optimistic lock");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleOptimisticLockException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("modificados por otro usuario"));
    }

    @Test
    void handleOptimisticLockingFailureException_ShouldReturnConflict() {
        when(i18nService.getMessage(MessageKey.ERROR_OPTIMISTIC_LOCK))
                .thenReturn(
                        "Los datos fueron modificados por otro usuario. Por favor, recarga la página e intenta nuevamente.");

        OptimisticLockingFailureException exception = new OptimisticLockingFailureException(
                "Optimistic locking failure");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleOptimisticLockingFailureException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("modificados por otro usuario"));
    }

    @Test
    void handlePessimisticLockingFailureException_ShouldReturnLocked() {
        when(i18nService.getMessage(MessageKey.ERROR_PESSIMISTIC_LOCK))
                .thenReturn("El recurso está siendo usado por otro usuario. Por favor, intenta en unos momentos.");

        PessimisticLockingFailureException exception = new PessimisticLockingFailureException(
                "Pessimistic locking failure");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handlePessimisticLockingFailureException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.LOCKED, response.getStatusCode());
        assertEquals(423, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("siendo usado"));
    }

    @Test
    void handleConcurrencyException_ShouldReturnConflict() {
        when(i18nService.getMessage(MessageKey.ERROR_OPTIMISTIC_LOCK))
                .thenReturn("Los datos fueron modificados por otro usuario.");

        ConcurrencyException exception = new ConcurrencyException("Product", 1);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleConcurrencyException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("modificados"));
    }

    @Test
    void handleStockLockException_ShouldReturnLocked() {
        when(i18nService.getMessage(MessageKey.ERROR_PESSIMISTIC_LOCK))
                .thenReturn("El recurso está siendo usado.");

        StockLockException exception = new StockLockException("Stock lock error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleStockLockException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.LOCKED, response.getStatusCode());
        assertEquals(423, response.getBody().getStatus());
    }

    @Test
    void handleBadCredentials_ShouldReturnUnauthorized() {
        when(i18nService.getMessage(MessageKey.ERROR_AUTH_BAD_CREDENTIALS))
                .thenReturn("Usuario o contraseña incorrectos");

        BadCredentialsException exception = new BadCredentialsException("Bad credentials");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBadCredentials(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("incorrectos"));
    }

    @Test
    void handleJwtException_ShouldReturnUnauthorized() {
        when(i18nService.getMessage(MessageKey.ERROR_AUTH_JWT_INVALID))
                .thenReturn("Token JWT inválido");

        JwtException exception = new JwtException("JWT error");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleJwtException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("inválido"));
    }

    @Test
    void handleGeneralException_ShouldReturnInternalServerError() {
        when(i18nService.getMessage(MessageKey.ERROR_INTERNAL_SERVER_ERROR))
                .thenReturn("Se ha producido un error interno. Por favor, inténtelo de nuevo más tarde.");

        Exception exception = new Exception("Generic error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGeneralException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
    }
}
