package com.economato.inventory.infrastructure.adapter.in.messaging.kafka.consumer;

import com.economato.inventory.application.dto.event.ForecastResultEvent;
import com.economato.inventory.application.usecase.StockAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

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
        log.info("Recibido resultado de predicción para producto ID: {}. Proyectado: {}", 
            event.getProductId(), event.getProjectedConsumption());
        
        try {
            stockAlertService.updatePredictionFromForecast(event.getProductId(), event.getProjectedConsumption());
        } catch (Exception e) {
            log.error("Error al procesar resultado de predicción: {}", e.getMessage(), e);
        }
    }
}
