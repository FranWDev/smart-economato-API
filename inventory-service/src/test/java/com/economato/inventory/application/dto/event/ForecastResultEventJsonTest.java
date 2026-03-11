package com.economato.inventory.application.dto.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Unit tests for JSON serialization/deserialization of {@link ForecastResultEvent}.
 *
 * These tests are lightweight and do not require any Spring context, allowing
 * them to run in environments where Docker (and therefore Testcontainers) is
 * unavailable.
 */
public class ForecastResultEventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void whenDeserializeWithOffsetItShouldPopulateOffsetDateTime() throws JsonProcessingException {
        String json = "{"
                + "\"productId\":123,"
                + "\"projectedConsumption\":45.6,"
                + "\"calculatedAt\":\"2026-03-11T09:25:52.934665+00:00\","
                + "\"modelUsed\":\"x\"}"
                ;

        ForecastResultEvent event = mapper.readValue(json, ForecastResultEvent.class);
        assertThat(event).isNotNull();
        assertThat(event.getProductId()).isEqualTo(123);
        assertThat(event.getCalculatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-11T09:25:52.934665+00:00"));
    }

    @Test
    void whenSerializeOffsetDateTimeItShouldProduceIsoString() throws JsonProcessingException {
        ForecastResultEvent evt = ForecastResultEvent.builder()
                .productId(1)
                .projectedConsumption(new BigDecimal("10.0"))
                .calculatedAt(OffsetDateTime.parse("2026-03-11T09:25:52.934665+00:00"))
                .modelUsed("m").build();

        String out = mapper.writeValueAsString(evt);
        // The ISO representation may use 'Z' for UTC or '+00:00'; both are acceptable
        // so we parse the value back and assert equality with the original offset.
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(out);
        String ts = node.get("calculatedAt").asText();
        assertThat(OffsetDateTime.parse(ts)).isEqualTo(evt.getCalculatedAt());
    }
}
