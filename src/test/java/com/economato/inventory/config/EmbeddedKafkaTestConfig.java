package com.economato.inventory.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Configuración de Kafka embebido para tests.
 * Permite ejecutar tests sin necesidad de un broker Kafka externo.
 */
@TestConfiguration
@EmbeddedKafka(
    partitions = 1,
    topics = {"inventory-audit-events", "recipe-audit-events"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9093",
        "port=9093"
    }
)
@Profile("test")
public class EmbeddedKafkaTestConfig {
    // La anotación @EmbeddedKafka ya configura el broker embebido automáticamente
}
