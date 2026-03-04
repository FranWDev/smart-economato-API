package com.economato.inventory.event;

/**
 * Event published when a CircuitBreaker transitions to CLOSED state (recovery).
 */
public class CircuitBreakerClosedEvent {
    private final String instanceName;

    public CircuitBreakerClosedEvent(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getInstanceName() {
        return instanceName;
    }
}
