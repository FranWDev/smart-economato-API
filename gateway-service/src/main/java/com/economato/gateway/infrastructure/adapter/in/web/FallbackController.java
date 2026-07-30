package com.economato.gateway.infrastructure.adapter.in.web;

import com.economato.gateway.application.port.in.FallbackUseCase;
import com.economato.gateway.domain.model.FallbackResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Locale;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private final FallbackUseCase fallbackUseCase;

    public FallbackController(FallbackUseCase fallbackUseCase) {
        this.fallbackUseCase = fallbackUseCase;
    }

    @GetMapping("/inventory")
    @PostMapping("/inventory")
    public Mono<ResponseEntity<FallbackResponse>> inventoryFallback(Locale locale) {
        return fallbackUseCase.getInventoryFallback(locale)
                .map(response -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }

    @GetMapping("/predictor")
    @PostMapping("/predictor")
    public Mono<ResponseEntity<FallbackResponse>> predictorFallback(Locale locale) {
        return fallbackUseCase.getPredictorFallback(locale)
                .map(response -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }

    @GetMapping("/mcp")
    @PostMapping("/mcp")
    public Mono<ResponseEntity<FallbackResponse>> mcpFallback(Locale locale) {
        return fallbackUseCase.getMcpFallback(locale)
                .map(response -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
