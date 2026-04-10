package com.economato.inventory.infrastructure.config.messaging;

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

import com.economato.inventory.application.dto.event.InventoryAuditEvent;
import com.economato.inventory.application.dto.event.BlockchainAuditEvent;
import com.economato.inventory.application.dto.event.OrderAuditEvent;
import com.economato.inventory.application.dto.event.PresenceAuditEvent;
import com.economato.inventory.application.dto.event.RecipeAuditEvent;
import com.economato.inventory.application.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.application.dto.event.ForecastResultEvent;
import com.economato.inventory.application.dto.event.StockPredictionEvent;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de Kafka para el sistema de auditoría asíncrona.
 * Solo se activa en perfiles NO-TEST.
 */
@EnableKafka
@Configuration
@Profile({ "!test", "kafka-test" })
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public NewTopic stockPredictionTopic() {
        return TopicBuilder.name(AuditEventProducer.STOCK_PREDICTION_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryAuditTopic() {
        return TopicBuilder.name(AuditEventProducer.INVENTORY_AUDIT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic recipeAuditTopic() {
        return TopicBuilder.name(AuditEventProducer.RECIPE_AUDIT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderAuditTopic() {
        return TopicBuilder.name(AuditEventProducer.ORDER_AUDIT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic recipeCookingAuditTopic() {
        return TopicBuilder.name(AuditEventProducer.RECIPE_COOKING_AUDIT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic presenceAuditTopic() {
        return TopicBuilder.name(AuditEventProducer.PRESENCE_AUDIT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic forecastUpdatesTopic() {
        return TopicBuilder.name("forecast-updates")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ledgerBlockTopic() {
        return TopicBuilder.name(AuditEventProducer.LEDGER_BLOCK_TOPIC)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "-1")
                .config("cleanup.policy", "compact")
                .build();
    }

    /**
     * Configuración común del productor
     */
    private Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        /* Que es acks_config = all? 
         * Garantiza que el productor reciba una confirmación de que el mensaje ha sido replicado a todas las réplicas ISR 
         * (In-Sync Replicas) antes de considerarlo enviado con éxito.
         */
        props.put(ProducerConfig.ACKS_CONFIG, "all"); 
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

    @Bean
    public ProducerFactory<String, ForecastResultEvent> forecastResultProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, ForecastResultEvent> forecastResultKafkaTemplate() {
        return new KafkaTemplate<>(forecastResultProducerFactory());
    }

    @Bean
    public ProducerFactory<String, StockPredictionEvent> stockPredictionProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, StockPredictionEvent> stockPredictionKafkaTemplate() {
        return new KafkaTemplate<>(stockPredictionProducerFactory());
    }

    @Bean
    public ProducerFactory<String, PresenceAuditEvent> presenceAuditProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, PresenceAuditEvent> presenceAuditKafkaTemplate() {
        return new KafkaTemplate<>(presenceAuditProducerFactory());
    }

    @Bean
    public ProducerFactory<String, BlockchainAuditEvent> blockchainAuditProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, BlockchainAuditEvent> blockchainAuditKafkaTemplate() {
        return new KafkaTemplate<>(blockchainAuditProducerFactory());
    }

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

        ErrorHandlingDeserializer<InventoryAuditEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                deserializer);

        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs("inventory-audit-consumer-group"),
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryAuditEvent> inventoryAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryAuditEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryAuditConsumerFactory());
        factory.setConcurrency(3); 
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, RecipeAuditEvent> recipeAuditConsumerFactory() {
        // Configurar el deserializer SOLO programáticamente
        JsonDeserializer<RecipeAuditEvent> deserializer = new JsonDeserializer<>(RecipeAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<RecipeAuditEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                deserializer);

        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs("recipe-audit-consumer-group"),
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RecipeAuditEvent> recipeAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RecipeAuditEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(recipeAuditConsumerFactory());
        factory.setConcurrency(3); // 3 hilos concurrentes
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, OrderAuditEvent> orderAuditConsumerFactory() {
        JsonDeserializer<OrderAuditEvent> deserializer = new JsonDeserializer<>(OrderAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<OrderAuditEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                deserializer);

        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs("order-audit-consumer-group"),
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderAuditEvent> orderAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderAuditEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderAuditConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, RecipeCookingAuditEvent> recipeCookingAuditConsumerFactory() {
        JsonDeserializer<RecipeCookingAuditEvent> deserializer = new JsonDeserializer<>(RecipeCookingAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<RecipeCookingAuditEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                deserializer);

        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs("recipe-cooking-audit-consumer-group"),
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RecipeCookingAuditEvent> recipeCookingAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RecipeCookingAuditEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(recipeCookingAuditConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, ForecastResultEvent> forecastResultConsumerFactory() {
        JsonDeserializer<ForecastResultEvent> deserializer = new JsonDeserializer<>(ForecastResultEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<ForecastResultEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                deserializer);

        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs("forecast-result-consumer-group"),
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ForecastResultEvent> forecastResultKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ForecastResultEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(forecastResultConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PresenceAuditEvent> presenceAuditConsumerFactory() {
        JsonDeserializer<PresenceAuditEvent> deserializer = new JsonDeserializer<>(PresenceAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<PresenceAuditEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                deserializer);

        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs("presence-audit-consumer-group"),
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PresenceAuditEvent> presenceAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PresenceAuditEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(presenceAuditConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, BlockchainAuditEvent> blockchainAuditConsumerFactory() {
        JsonDeserializer<BlockchainAuditEvent> deserializer = new JsonDeserializer<>(BlockchainAuditEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<BlockchainAuditEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(
                deserializer);

        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs("ledger-block-consumer-group"),
                new StringDeserializer(),
                errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BlockchainAuditEvent> blockchainAuditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, BlockchainAuditEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(blockchainAuditConsumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
