package com.economato.inventory.infrastructure.adapter.in.messaging.kafka;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import com.economato.inventory.application.dto.event.ForecastResultEvent;
import com.economato.inventory.application.dto.event.ForecastResultType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.StockPrediction;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockPredictionRepository;
import com.economato.inventory.infrastructure.config.KafkaTestContainerConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration Test for the Forecast flow.
 * 
 * NOTE: This test uses a real Kafka container via Testcontainers, but it mocks the 
 * "Predictor Service" (Python) by sending the message directly through KafkaTemplate.
 * This allows us to verify the Java Consumer and Database logic in isolation but with
 * real infrastructure, which is a standard practice for microservices integration tests.
 */
@SpringBootTest
@ActiveProfiles({ "kafka-test" })
@Import(KafkaTestContainerConfig.class)
public class ForecastIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KafkaTemplate<String, ForecastResultEvent> kafkaTemplate;

    @Autowired
    private StockPredictionRepository predictionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void testConsumeForecastResultUpdatesDatabase() {
        // 1. Setup: Crear un producto de prueba
        Product product = new Product();
        product.setName("Producto Test Prophet");
        product.setUnitPrice(BigDecimal.ONE);
        product.setUnit("KG");
        product.setProductCode("PROPHET-001");
        final Product savedProduct = productRepository.save(product);
        Integer productId = savedProduct.getId();

        // 2. Simular mensaje de Python a Kafka
        ForecastResultEvent event = ForecastResultEvent.builder()
                .productId(productId)
                .projectedConsumption(new BigDecimal("125.50"))
                .calculatedAt(OffsetDateTime.now())
                .modelUsed("Meta Prophet v1.1")
            .confidenceScore(new BigDecimal("0.90"))
            .eventType(ForecastResultType.PREDICTION)
                .build();

        kafkaTemplate.send("forecast-updates", String.valueOf(productId), event);

        // 3. Verificar que el listener de Java procesó el mensaje y actualizó la DB
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<StockPrediction> prediction = predictionRepository.findById(productId);
                    assertThat(prediction).isPresent();
                    assertThat(prediction.get().getProjectedConsumption()).isEqualByComparingTo("125.50");
                });
    }
}
