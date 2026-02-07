package com.economato.inventory.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.economato.inventory.dto.event.InventoryAuditEvent;
import com.economato.inventory.dto.event.OrderAuditEvent;
import com.economato.inventory.dto.event.RecipeAuditEvent;
import com.economato.inventory.dto.event.RecipeCookingAuditEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de Kafka para el sistema de auditoría asíncrona.
 * Solo se activa en perfiles NO-TEST.
 */
@EnableKafka
@Configuration
@Profile("!test")
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ========== PRODUCER CONFIGURATION ==========

    /**
     * Configuración común del productor
     */
    private Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // Required for idempotent producer
        props.put(ProducerConfig.RETRIES_CONFIG, 3); // Reintentos en caso de fallo
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Evita duplicados
        return props;
    }

    @Bean
    public ProducerFactory<String, InventoryAuditEvent> inventoryAuditProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, InventoryAuditEvent> inventoryAuditKafkaTemplate() {
        return new KafkaTemplate<>(inventoryAuditProducerFactory());
    }

    @Bean
    public ProducerFactory<String, RecipeAuditEvent> recipeAuditProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, RecipeAuditEvent> recipeAuditKafkaTemplate() {
        return new KafkaTemplate<>(recipeAuditProducerFactory());
    }

    @Bean
    public ProducerFactory<String, OrderAuditEvent> orderAuditProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, OrderAuditEvent> orderAuditKafkaTemplate() {
        return new KafkaTemplate<>(orderAuditProducerFactory());
    }

    @Bean
    public ProducerFactory<String, RecipeCookingAuditEvent> recipeCookingAuditProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, RecipeCookingAuditEvent> recipeCookingAuditKafkaTemplate() {
        return new KafkaTemplate<>(recipeCookingAuditProducerFactory());
    }

    // ========== CONSUMER CONFIGURATION ==========

    /**
     * Configuración común del consumidor
     */
    private Map<String, Object> consumerConfigs(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // NO configurar JsonDeserializer.TRUSTED_PACKAGES aquí para evitar conflicto
        return props;
    }

    @Bean
    public ConsumerFactory<String, InventoryAuditEvent> inventoryAuditConsumerFactory() {
        // Configurar el deserializer SOLO programáticamente
        JsonDeserializer<InventoryAuditEvent> deserializer = new JsonDeserializer<>(InventoryAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<InventoryAuditEvent> errorHandlingDeserializer = 
            new ErrorHandlingDeserializer<>(deserializer);

        return new DefaultKafkaConsumerFactory<>(
            consumerConfigs("inventory-audit-consumer-group"),
            new StringDeserializer(),
            errorHandlingDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryAuditEvent> 
            inventoryAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryAuditEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryAuditConsumerFactory());
        factory.setConcurrency(3); // 3 hilos concurrentes
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD
        );
        return factory;
    }

    @Bean
    public ConsumerFactory<String, RecipeAuditEvent> recipeAuditConsumerFactory() {
        // Configurar el deserializer SOLO programáticamente
        JsonDeserializer<RecipeAuditEvent> deserializer = new JsonDeserializer<>(RecipeAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<RecipeAuditEvent> errorHandlingDeserializer = 
            new ErrorHandlingDeserializer<>(deserializer);

        return new DefaultKafkaConsumerFactory<>(
            consumerConfigs("recipe-audit-consumer-group"),
            new StringDeserializer(),
            errorHandlingDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RecipeAuditEvent> 
            recipeAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RecipeAuditEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(recipeAuditConsumerFactory());
        factory.setConcurrency(3); // 3 hilos concurrentes
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD
        );
        return factory;
    }

    @Bean
    public ConsumerFactory<String, OrderAuditEvent> orderAuditConsumerFactory() {
        // Configurar el deserializer SOLO programáticamente
        JsonDeserializer<OrderAuditEvent> deserializer = new JsonDeserializer<>(OrderAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<OrderAuditEvent> errorHandlingDeserializer = 
            new ErrorHandlingDeserializer<>(deserializer);

        return new DefaultKafkaConsumerFactory<>(
            consumerConfigs("order-audit-consumer-group"),
            new StringDeserializer(),
            errorHandlingDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderAuditEvent> 
            orderAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderAuditEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderAuditConsumerFactory());
        factory.setConcurrency(3); // 3 hilos concurrentes
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD
        );
        return factory;
    }

    @Bean
    public ConsumerFactory<String, RecipeCookingAuditEvent> recipeCookingAuditConsumerFactory() {
        // Configurar el deserializer SOLO programáticamente
        JsonDeserializer<RecipeCookingAuditEvent> deserializer = new JsonDeserializer<>(RecipeCookingAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<RecipeCookingAuditEvent> errorHandlingDeserializer = 
            new ErrorHandlingDeserializer<>(deserializer);

        return new DefaultKafkaConsumerFactory<>(
            consumerConfigs("recipe-cooking-audit-consumer-group"),
            new StringDeserializer(),
            errorHandlingDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RecipeCookingAuditEvent> 
            recipeCookingAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RecipeCookingAuditEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(recipeCookingAuditConsumerFactory());
        factory.setConcurrency(3); // 3 hilos concurrentes
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD
        );
        return factory;
    }
}
