package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ErrorResponse;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiChatLimitReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiChatNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiConcurrentStreamException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiKeyNotFoundException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiMaxMessagesReachedException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiProviderDisabledException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiRateLimitExceededException;
import com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception.AiStreamException;

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
        return ResponseEntity.status(422)
                .body(new ErrorResponse(422, ex.getMessage(), LocalDateTime.now()));
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
