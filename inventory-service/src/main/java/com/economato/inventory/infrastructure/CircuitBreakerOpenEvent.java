package com.economato.inventory.infrastructure;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * publicado cuando el circuito se abre, para que el frontend pueda mostrar un mensaje de error específico
 */
@Getter
@RequiredArgsConstructor
public class CircuitBreakerOpenEvent {
    private final String instanceName;
}
