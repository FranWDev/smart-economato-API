package com.economato.inventory.application.dto.stock.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
                + "\"modelUsed\":\"x\","
                + "\"confidenceScore\":0.91,"
                + "\"eventType\":\"PREDICTION\"}"
                ;

        ForecastResultEvent event = mapper.readValue(json, ForecastResultEvent.class);
        assertThat(event).isNotNull();
        assertThat(event.getProductId()).isEqualTo(123);
        assertThat(event.getCalculatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-11T09:25:52.934665+00:00"));
        assertThat(event.getConfidenceScore()).isEqualByComparingTo("0.91");
        assertThat(event.getEventType()).isEqualTo(ForecastResultType.PREDICTION);
    }

    @Test
    void whenSerializeOffsetDateTimeItShouldProduceIsoString() throws JsonProcessingException {
        ForecastResultEvent evt = ForecastResultEvent.builder()
                .productId(1)
                .projectedConsumption(new BigDecimal("10.0"))
                .calculatedAt(OffsetDateTime.parse("2026-03-11T09:25:52.934665+00:00"))
            .modelUsed("m")
            .confidenceScore(new BigDecimal("0.85"))
            .eventType(ForecastResultType.PREDICTION)
            .build();

        String out = mapper.writeValueAsString(evt);
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(out);
        String ts = node.get("calculatedAt").asText();
        assertThat(OffsetDateTime.parse(ts)).isEqualTo(evt.getCalculatedAt());
    }
}
