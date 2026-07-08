package com.economato.inventory.infrastructure.config.shared.database;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuración de Kafka para el perfil de TEST
 * Deshabilita Kafka completamente en tests
 */
@Configuration
@Profile("test")
public class KafkaTestConfig {
    // No habilitamos @EnableKafka en tests
    // Esto evita que se inicialicen los listeners y productores de Kafka
}
