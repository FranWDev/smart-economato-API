package com.economato.inventory.infrastructure;

public class CircuitBreakerClosedEvent {
    private final String instanceName;

    public CircuitBreakerClosedEvent(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getInstanceName() {
        return instanceName;
    }
}
