package com.economato.inventory.infrastructure;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Event published when a Circuit Breaker enters the OPEN state.
 */
@Getter
@RequiredArgsConstructor
public class CircuitBreakerOpenEvent {
    private final String instanceName;
}
