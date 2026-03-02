package com.economato.inventory.config;

import com.economato.inventory.service.prediction.HoltWintersForecaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de los componentes del motor de predicción.
 * Registra {@link HoltWintersForecaster} como bean singleton para que
 * pueda ser inyectado en {@code StockAlertService} via constructor.
 */
@Configuration
public class PredictionConfig {

    /**
     * Instancia del forecaster con coeficientes por defecto
     * (α=0.3, β=0.1, γ=0.2 — conservadores, apropiados para hostelería).
     */
    @Bean
    public HoltWintersForecaster holtWintersForecaster() {
        return new HoltWintersForecaster();
    }
}
