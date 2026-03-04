package com.economato.inventory.service.notification;

/**
 * Error/Alert codes sent via WebSocket to frontend.
 * Frontend is responsible for translating these codes to user's locale
 * and determining if the failure is partial or critical.
 */
public enum AlertCode {
    // Failure codes
    DB_FAILURE("DB_FAILURE", "Primary database is down"),
    REDIS_FAILURE("REDIS_FAILURE", "Redis cache is unavailable"),
    KAFKA_FAILURE("KAFKA_FAILURE", "Kafka messaging is unavailable"),
    REPLICA_FAILURE("REPLICA_FAILURE", "Database replica is unavailable"),
    
    // Recovery codes
    DB_RECOVERED("DB_RECOVERED", "Primary database is back online"),
    REDIS_RECOVERED("REDIS_RECOVERED", "Redis cache is back online"),
    KAFKA_RECOVERED("KAFKA_RECOVERED", "Kafka messaging is back online"),
    REPLICA_RECOVERED("REPLICA_RECOVERED", "Database replica is back online");

    private final String code;
    private final String description;

    AlertCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
