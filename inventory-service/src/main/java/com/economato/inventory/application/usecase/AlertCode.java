package com.economato.inventory.application.usecase;

/**
 * Códigos de error/alerta enviados por WebSocket al frontend.
 * El frontend es responsable de traducir estos códigos a la locale del usuario
 * y determinar si el fallo es parcial o crítico.
 */
public enum AlertCode {
    // Códigos de fallo
    DB_FAILURE("DB_FAILURE", "Primary database is down"),
    REDIS_FAILURE("REDIS_FAILURE", "Redis cache is unavailable"),
    KAFKA_FAILURE("KAFKA_FAILURE", "Kafka messaging is unavailable"),
    REPLICA_FAILURE("REPLICA_FAILURE", "Database replica is unavailable"),
    
    // Códigos de recuperación
    DB_RECOVERED("DB_RECOVERED", "Primary database is back online"),
    REDIS_RECOVERED("REDIS_RECOVERED", "Redis cache is back online"),
    KAFKA_RECOVERED("KAFKA_RECOVERED", "Kafka messaging is back online"),
    REPLICA_RECOVERED("REPLICA_RECOVERED", "Database replica is back online"),
    
    // Códigos de crisis alimentaria
    FOOD_CRISIS_ACTIVATED("FOOD_CRISIS_ACTIVATED", "Food safety crisis activated"),
    FOOD_CRISIS_LIFTED("FOOD_CRISIS_LIFTED", "Food safety crisis lifted"),

    // Códigos de predicción
    STOCK_PREDICTION_TRIGGERED("STOCK_PREDICTION_TRIGGERED", "Stock prediction has been triggered"),

    // Cambio de rol temporal
    ROLE_ESCALATION_CHANGED("ROLE_ESCALATION_CHANGED", "Temporary role escalation state changed");

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
