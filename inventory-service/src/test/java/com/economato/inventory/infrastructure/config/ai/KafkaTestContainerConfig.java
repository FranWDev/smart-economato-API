package com.economato.inventory.infrastructure.config.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestContainerConfig {

    /**
     * Provides a shared KafkaContainer for integration tests. The container is
     * started lazily during bean creation so that class-loading does not fail
     * when Docker is unavailable (e.g. on certain CI agents).
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public KafkaContainer kafkaContainer() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
        try {
            container.start();
            if (container.isRunning()) {
                System.setProperty("spring.kafka.bootstrap-servers", container.getBootstrapServers());
                System.out.println("[KafkaTestContainerConfig] Kafka container started at: " + container.getBootstrapServers());
            } else {
                System.err.println("[KafkaTestContainerConfig] Kafka container started but is not running.");
            }
        } catch (Exception e) {
            // In environments without Docker, simply log and continue. Tests may
            // choose to skip or fail gracefully.
            System.err.println("[KafkaTestContainerConfig] Could not start Kafka container: " + e.getMessage());
        }
        return container;
    }
}
