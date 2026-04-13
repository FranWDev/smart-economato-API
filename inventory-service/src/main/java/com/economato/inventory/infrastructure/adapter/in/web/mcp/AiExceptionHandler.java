package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.infrastructure.adapter.in.web.AiStreamException;
import com.economato.inventory.infrastructure.adapter.in.web.ErrorResponse;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiChatLimitReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiChatNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiConcurrentStreamException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiKeyNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiMaxMessagesReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiProviderDisabledException;
import com.economato.inventory.infrastructure.adapter.in.web.mcp.exception.AiRateLimitExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "com.economato.inventory.infrastructure.adapter.in.web.mcp")
public class AiExceptionHandler {

    @ExceptionHandler(AiChatNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAiChatNotFound(AiChatNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AiProviderDisabledException.class)
    public ResponseEntity<ErrorResponse> handleAiProviderDisabled(AiProviderDisabledException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AiKeyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAiKeyNotFound(AiKeyNotFoundException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(AiChatLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleAiChatLimitReached(AiChatLimitReachedException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(AiMaxMessagesReachedException.class)
    public ResponseEntity<ErrorResponse> handleAiMaxMessagesReached(AiMaxMessagesReachedException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler({AiRateLimitExceededException.class, AiConcurrentStreamException.class})
    public ResponseEntity<ErrorResponse> handleAiRateLimited(RuntimeException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "60");
        return new ResponseEntity<>(
                new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage(), LocalDateTime.now()),
                headers,
                HttpStatus.TOO_MANY_REQUESTS
        );
    }

    @ExceptionHandler(AiStreamException.class)
    public ResponseEntity<ErrorResponse> handleAiStreamException(AiStreamException ex) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), message, LocalDateTime.now()));
    }
}
