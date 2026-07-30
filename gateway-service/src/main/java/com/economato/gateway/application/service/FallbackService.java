package com.economato.gateway.application.service;

import com.economato.gateway.application.port.in.FallbackUseCase;
import com.economato.gateway.domain.model.FallbackResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Service
public class FallbackService implements FallbackUseCase {

    private final MessageSource messageSource;

    public FallbackService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public Mono<FallbackResponse> getInventoryFallback(Locale locale) {
        return Mono.fromCallable(() -> buildResponse("fallback.inventory", locale));
    }

    @Override
    public Mono<FallbackResponse> getPredictorFallback(Locale locale) {
        return Mono.fromCallable(() -> buildResponse("fallback.predictor", locale));
    }

    @Override
    public Mono<FallbackResponse> getMcpFallback(Locale locale) {
        return Mono.fromCallable(() -> buildResponse("fallback.mcp", locale));
    }

    private FallbackResponse buildResponse(String keyPrefix, Locale locale) {
        String code = messageSource.getMessage(keyPrefix + ".code", null, locale);
        String message = messageSource.getMessage(keyPrefix + ".message", null, locale);
        return FallbackResponse.create(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                code,
                message
        );
    }
}
