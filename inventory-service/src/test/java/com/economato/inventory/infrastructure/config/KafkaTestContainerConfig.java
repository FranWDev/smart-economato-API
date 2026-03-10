package com.economato.inventory.infrastructure.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class KafkaTestContainerConfig {

    private static final KafkaContainer KAFKA_CONTAINER;

    static {
        KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
        KAFKA_CONTAINER.start();
        System.setProperty("spring.kafka.bootstrap-servers", KAFKA_CONTAINER.getBootstrapServers());
    }

    @Bean
    public KafkaContainer kafkaContainer() {
        return KAFKA_CONTAINER;
    }
}
