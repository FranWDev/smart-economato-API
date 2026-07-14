package com.economato.inventory.infrastructure.adapter.in.messaging.stock.kafka.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.economato.inventory.application.dto.stock.event.ForecastResultEvent;
import com.economato.inventory.application.dto.stock.event.ForecastResultType;
import com.economato.inventory.application.usecase.stock.StockAlertService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForecastResultConsumerTest {

    @Mock
    private StockAlertService stockAlertService;

    @InjectMocks
    private ForecastResultConsumer consumer;

    @Test
    void consumeForecastResult_persistsWhenTypeIsPrediction() {
        ForecastResultEvent event = ForecastResultEvent.builder()
                .productId(10)
                .projectedConsumption(new BigDecimal("12.50"))
                .calculatedAt(OffsetDateTime.now())
                .eventType(ForecastResultType.PREDICTION)
                .build();

        when(stockAlertService.classifyForecastResult(10, new BigDecimal("12.50")))
            .thenReturn(ForecastResultType.PREDICTION);

        consumer.consumeForecastResult(event);

        verify(stockAlertService).updatePredictionFromForecast(eq(10), eq(new BigDecimal("12.50")), any());
    }

    @Test
    void consumeForecastResult_usesPredictionAsDefaultWhenTypeMissing() {
        ForecastResultEvent event = ForecastResultEvent.builder()
                .productId(11)
                .projectedConsumption(new BigDecimal("8.00"))
                .build();

        when(stockAlertService.classifyForecastResult(11, new BigDecimal("8.00")))
            .thenReturn(ForecastResultType.PREDICTION);

        consumer.consumeForecastResult(event);

        verify(stockAlertService).updatePredictionFromForecast(eq(11), eq(new BigDecimal("8.00")), any());
    }

    @Test
        void consumeForecastResult_persistsAlsoWhenInferredTypeIsAlert() {
        ForecastResultEvent event = ForecastResultEvent.builder()
                .productId(12)
                .projectedConsumption(new BigDecimal("7.00"))
                .eventType(ForecastResultType.ALERT)
                .build();

        when(stockAlertService.classifyForecastResult(12, new BigDecimal("7.00")))
            .thenReturn(ForecastResultType.ALERT);

        consumer.consumeForecastResult(event);

        verify(stockAlertService).updatePredictionFromForecast(eq(12), eq(new BigDecimal("7.00")), any());
    }

    @Test
    void consumeForecastResult_doesNotPersistWhenPayloadInvalid() {
        ForecastResultEvent event = ForecastResultEvent.builder()
                .eventType(ForecastResultType.PREDICTION)
                .build();

        consumer.consumeForecastResult(event);

        verify(stockAlertService, Mockito.never()).updatePredictionFromForecast(any(), any(), any());
    }

        @Test
        void consumeForecastResult_persistsWhenReceivedTypeDiffersFromInferredType() {
        ForecastResultEvent event = ForecastResultEvent.builder()
            .productId(13)
            .projectedConsumption(new BigDecimal("20.00"))
            .eventType(ForecastResultType.PREDICTION)
            .build();

        when(stockAlertService.classifyForecastResult(13, new BigDecimal("20.00")))
            .thenReturn(ForecastResultType.ALERT);

        consumer.consumeForecastResult(event);

        verify(stockAlertService).updatePredictionFromForecast(eq(13), eq(new BigDecimal("20.00")), any());
        }
}
