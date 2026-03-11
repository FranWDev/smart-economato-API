package com.economato.inventory.infrastructure.adapter.in.messaging.kafka.consumer;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.economato.inventory.application.dto.event.ForecastResultEvent;
import com.economato.inventory.application.dto.event.ForecastResultType;
import com.economato.inventory.application.usecase.StockAlertService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile({ "!test", "kafka-test" })
public class ForecastResultConsumer {

    private final StockAlertService stockAlertService;

    @KafkaListener(
        topics = "forecast-updates",
        groupId = "forecast-result-consumer-group",
        containerFactory = "forecastResultKafkaListenerContainerFactory"
    )
    public void consumeForecastResult(ForecastResultEvent event) {
        if (event == null) {
            log.warn("Mensaje de forecast nulo recibido en topic forecast-updates. Se descarta.");
            return;
        }

        if (event.getProductId() == null) {
            log.warn("Mensaje de forecast descartado por productId nulo. payload={}", event);
            return;
        }

        if (event.getProjectedConsumption() == null) {
            log.warn("Forecast descartado para producto {} por projectedConsumption nulo. payload={}",
                    event.getProductId(), event);
            return;
        }

        if (event.getProjectedConsumption().signum() < 0) {
            log.warn("Forecast descartado para producto {} por projectedConsumption negativo: {}",
                    event.getProductId(), event.getProjectedConsumption());
            return;
        }

        try {
            ForecastResultType inferredType = stockAlertService.classifyForecastResult(
                    event.getProductId(),
                    event.getProjectedConsumption());

            if (event.getEventType() != null && event.getEventType() != inferredType) {
                log.warn("Inconsistencia de tipo para producto {}. Recibido={}, inferido={}. Se usará inferido.",
                        event.getProductId(), event.getEventType(), inferredType);
            }

            LocalDateTime calculatedAt = event.getCalculatedAt() != null
                    ? event.getCalculatedAt().toLocalDateTime()
                    : LocalDateTime.now();

            // Siempre persistimos la predicción oficial; la clasificación ALERT/PREDICTION
            // se usa para validación y trazabilidad.
            stockAlertService.updatePredictionFromForecast(
                    event.getProductId(),
                    event.getProjectedConsumption(),
                    calculatedAt);

            log.info("Forecast procesado para producto {}: tipo={}, projectedConsumption={}",
                    event.getProductId(), inferredType, event.getProjectedConsumption());
        } catch (Exception e) {
            log.error("Error al procesar resultado de predicción: {}", e.getMessage(), e);
        }
    }
}
