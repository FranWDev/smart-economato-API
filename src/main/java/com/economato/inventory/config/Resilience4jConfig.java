package com.economato.inventory.config;

import com.economato.inventory.event.CircuitBreakerOpenEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Resilience4jConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(this::onEntryAdded)
                .onEntryRemoved(this::onEntryRemoved)
                .onEntryReplaced(this::onEntryReplaced);

        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::setupCircuitBreakerListener);
    }

    private void onEntryAdded(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
        setupCircuitBreakerListener(entryAddedEvent.getAddedEntry());
    }

    private void onEntryRemoved(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
        log.info("CircuitBreaker removed: {}", entryRemoveEvent.getRemovedEntry().getName());
    }

    private void onEntryReplaced(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
        setupCircuitBreakerListener(entryReplacedEvent.getNewEntry());
    }

    private void setupCircuitBreakerListener(CircuitBreaker circuitBreaker) {
        String cbName = circuitBreaker.getName();
        log.info("Setting up state transition listener for CircuitBreaker: {}", cbName);

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.info("CircuitBreaker '{}' changed state from {} to {}",
                            event.getCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());

                    if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                        handleOpenCircuit(cbName);
                    }
                });
    }

    private void handleOpenCircuit(String instanceName) {
        log.warn("CircuitBreaker [{}] is OPEN! Publishing event.", instanceName);
        eventPublisher.publishEvent(new CircuitBreakerOpenEvent(instanceName));
    }
}
