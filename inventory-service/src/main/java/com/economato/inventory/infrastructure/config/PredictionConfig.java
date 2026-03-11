package com.economato.inventory.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.economato.inventory.infrastructure.adapter.out.external.prediction.HoltWintersForecaster;

/**
 * Configuración de componentes legacy de predicción.
 *
 * <p>El bean de {@link HoltWintersForecaster} se conserva por compatibilidad y
 * referencia histórica, pero el flujo oficial de predicciones usa exclusivamente
 * el predictor de IA publicado por Kafka.</p>
 */
@Configuration
public class PredictionConfig {

    /**
     * @deprecated Bean legado. No debe usarse para generar predicciones oficiales.
     */
    @Bean
    @Deprecated
    public HoltWintersForecaster holtWintersForecaster() {
        return new HoltWintersForecaster();
    }
}
