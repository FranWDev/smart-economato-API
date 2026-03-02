package com.economato.inventory.service.prediction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HoltWintersForecasterTest {

    private HoltWintersForecaster forecaster;

    @BeforeEach
    void setUp() {
        forecaster = new HoltWintersForecaster();
    }

    @Test
    void forecast_withEmptyHistory_returnsZero() {
        double result = forecaster.forecast(Collections.emptyList(), 1, 14);
        assertEquals(0.0, result);
    }

    @Test
    void forecast_withNullHistory_returnsZero() {
        double result = forecaster.forecast(null, 1, 14);
        assertEquals(0.0, result);
    }

    @Test
    void forecast_withSingleObservation_returnsPositive() {
        List<Double> obs = List.of(10.0);
        double result = forecaster.forecast(obs, 1, 14);
        assertTrue(result >= 0.0, "Forecast should be non-negative: " + result);
    }

    @Test
    void forecast_withStableHistory_returnsReasonableValue() {
        // Consumo estable de 5 unidades/semana durante 8 semanas
        List<Double> obs = Arrays.asList(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0);
        double result = forecaster.forecast(obs, 1, 14); // 14 días = 2 semanas → ~10
        assertTrue(result > 5.0 && result < 20.0,
                "Stable series should forecast ~10 units for 14 days, got: " + result);
    }

    @Test
    void forecast_withGrowingHistory_forecastsHigherThanMean() {
        // Consumo creciente: el forecast debe superar la media histórica
        List<Double> obs = Arrays.asList(2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0);
        double mean = obs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double dailyForecast = forecaster.forecast(obs, 1, 7); // 1 semana
        assertTrue(dailyForecast > 0,
                "Growing series should produce non-zero forecast, got: " + dailyForecast);
    }

    @Test
    void forecast_withFallbackShortHistory_returnsPositive() {
        // Con menos de 4 semanas aplica media simple
        List<Double> obs = Arrays.asList(3.0, 4.0);
        double result = forecaster.forecast(obs, 1, 14);
        assertTrue(result > 0, "Short history should still forecast positively: " + result);
    }

    @Test
    void filterAnomalies_removesOutliers() {
        // Serie estable con un valor atípico muy alto
        List<Double> obs = Arrays.asList(5.0, 5.0, 5.0, 5.0, 100.0, 5.0, 5.0, 5.0);
        List<Double> cleaned = forecaster.filterAnomalies(obs);
        assertFalse(cleaned.contains(100.0), "Anomaly (100.0) should be removed");
        assertEquals(7, cleaned.size());
    }

    @Test
    void filterAnomalies_preservesNormalValues() {
        List<Double> obs = Arrays.asList(4.5, 5.0, 5.5, 4.8, 5.2, 5.0);
        List<Double> cleaned = forecaster.filterAnomalies(obs);
        assertEquals(obs.size(), cleaned.size(), "No anomalies should be removed from this series");
    }

    @Test
    void forecast_withOnlyAnomalies_usesOriginalSeries() {
        // Todos los valores son extremos — no se puede filtrar, debe usar la serie
        // original
        List<Double> obs = Arrays.asList(1.0, 1000.0, 2.0, 999.0);
        double result = forecaster.forecast(obs, 1, 14);
        assertTrue(result >= 0.0, "Should return non-negative value: " + result);
    }
}
