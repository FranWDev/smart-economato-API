package com.economato.gateway.application.port.in;

import com.economato.gateway.domain.model.FallbackResponse;
import reactor.core.publisher.Mono;

import java.util.Locale;

public interface FallbackUseCase {
    Mono<FallbackResponse> getInventoryFallback(Locale locale);
    Mono<FallbackResponse> getPredictorFallback(Locale locale);
    Mono<FallbackResponse> getMcpFallback(Locale locale);
}
