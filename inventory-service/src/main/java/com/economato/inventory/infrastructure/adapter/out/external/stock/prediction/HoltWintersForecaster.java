package com.economato.inventory.infrastructure.adapter.out.external.stock.prediction;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Forecaster legado conservado solo por compatibilidad histórica.
 *             Las predicciones oficiales del sistema se generan mediante el
 *             predictor de IA (Meta Prophet) y no deben sobrescribirse con esta implementación.
 *
 * Implementación de Holt-Winters Triple Exponential Smoothing en Java puro.
 *
 */
@Deprecated
public class HoltWintersForecaster {

    /** Mínimo de semanas necesarias para aplicar Holt-Winters. */
    private static final int MIN_WEEKS_FOR_HW = 1;

    /** Umbral de Z-score para filtrar semanas anómalas. */
    private static final double Z_SCORE_THRESHOLD = 2.5;
    
    // Coeficientes por defecto (conservadores, apropiados para demanda de
    // hostelería)
    private final double alpha; // suavizado del nivel
    private final double beta; // suavizado de la tendencia
    private final double gamma; // suavizado de la estacionalidad

    public HoltWintersForecaster() {
        this(0.3, 0.1, 0.2);
    }

    public HoltWintersForecaster(double alpha, double beta, double gamma) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
    }

    public double forecast(List<Double> weeklyObservations, int seasonPeriod, int horizonDays) {
        if (weeklyObservations == null || weeklyObservations.isEmpty()) {
            return 0.0;
        }

        List<Double> cleaned = filterAnomalies(weeklyObservations);

        if (cleaned.isEmpty()) {
            // Todos eran anomalías: usar media simple del conjunto original
            cleaned = weeklyObservations;
        }

        double weeklyForecast;
        if (cleaned.size() < MIN_WEEKS_FOR_HW) {
            weeklyForecast = simpleMean(cleaned);
        } else {
            weeklyForecast = holtwinters(cleaned, seasonPeriod);
        }

        // Convertir consumo-por-semana a consumo total en horizonDays
        double daysPerWeek = 7.0;
        return weeklyForecast * (horizonDays / daysPerWeek);
    }

    /**
     * Genera una serie de forecasts diarios para los próximos horizonDays.
     * El modelo trabaja a nivel semanal, por lo que proyecta semana a semana
     * y divide entre 7 para obtener el consumo diario.
     */
    public List<Double> forecastDaily(List<Double> weeklyObservations, int seasonPeriod, int horizonDays) {
        if (horizonDays <= 0) {
            return List.of();
        }

        if (weeklyObservations == null || weeklyObservations.isEmpty()) {
            return new ArrayList<>(java.util.Collections.nCopies(horizonDays, 0.0));
        }

        List<Double> cleaned = filterAnomalies(weeklyObservations);
        if (cleaned.isEmpty()) {
            cleaned = weeklyObservations;
        }

        int m = Math.max(1, seasonPeriod);
        double fallbackDaily = simpleMean(cleaned) / 7.0;

        if (cleaned.size() < MIN_WEEKS_FOR_HW) {
            return new ArrayList<>(java.util.Collections.nCopies(horizonDays, Math.max(0.0, fallbackDaily)));
        }

        HoltWintersState state = holtwintersFull(cleaned, m);
        int n = cleaned.size();
        int weeksToForecast = (int) Math.ceil(horizonDays / 7.0);

        List<Double> weekDailyRates = new ArrayList<>(weeksToForecast);
        for (int week = 0; week < weeksToForecast; week++) {
            int k = week + 1;
            int seasonIdx = (n + k) % m;
            double weekForecast = (state.level() + k * state.trend()) * state.seasonal().get(seasonIdx);
            weekForecast = Math.max(0.0, weekForecast);
            weekDailyRates.add(weekForecast / 7.0);
        }

        List<Double> daily = new ArrayList<>(horizonDays);
        for (int day = 0; day < horizonDays; day++) {
            int weekBucket = day / 7;
            daily.add(weekDailyRates.get(weekBucket));
        }
        return daily;
    }

    // -------------------------------------------------------------------------
    // Holt-Winters 
    // -------------------------------------------------------------------------

    private double holtwinters(List<Double> y, int m) {
        int n = y.size();

        double level = initialLevel(y, m);
        double trend = initialTrend(y, m);
        List<Double> seasonal = initialSeasonals(y, m);

        double lastForecast = level;

        for (int i = 0; i < n; i++) {
            double obs = y.get(i);
            double prevLevel = level;
            double prevTrend = trend;
            int seasonIdx = i % m;

            level = alpha * (obs / seasonal.get(seasonIdx)) + (1 - alpha) * (prevLevel + prevTrend);
            trend = beta * (level - prevLevel) + (1 - beta) * prevTrend;
            double newSeasonal = gamma * (obs / level) + (1 - gamma) * seasonal.get(seasonIdx);
            seasonal.set(seasonIdx, newSeasonal);

            lastForecast = (level + trend) * seasonal.get((i + 1) % m);
        }

        return Math.max(0.0, lastForecast);
    }

    private HoltWintersState holtwintersFull(List<Double> y, int m) {
        int n = y.size();

        double level = initialLevel(y, m);
        double trend = initialTrend(y, m);
        List<Double> seasonal = initialSeasonals(y, m);

        for (int i = 0; i < n; i++) {
            double obs = y.get(i);
            double prevLevel = level;
            double prevTrend = trend;
            int seasonIdx = i % m;

            level = alpha * (obs / seasonal.get(seasonIdx)) + (1 - alpha) * (prevLevel + prevTrend);
            trend = beta * (level - prevLevel) + (1 - beta) * prevTrend;
            double newSeasonal = gamma * (obs / level) + (1 - gamma) * seasonal.get(seasonIdx);
            seasonal.set(seasonIdx, newSeasonal);
        }

        return new HoltWintersState(level, trend, seasonal);
    }

    private record HoltWintersState(double level, double trend, List<Double> seasonal) {
    }

    private double initialLevel(List<Double> y, int m) {
        int end = Math.min(m, y.size());
        return y.subList(0, end).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double initialTrend(List<Double> y, int m) {
        if (y.size() < 2 * m) {
            return (y.get(y.size() - 1) - y.get(0)) / Math.max(1, y.size() - 1);
        }
        double sum = 0.0;
        for (int i = 0; i < m; i++) {
            sum += (y.get(i + m) - y.get(i)) / (double) m;
        }
        return sum / m;
    }

    private List<Double> initialSeasonals(List<Double> y, int m) {
        int nCycles = y.size() / m;
        List<Double> seasonals = new ArrayList<>();

        for (int i = 0; i < m; i++) {
            double avg = 0.0;
            int count = 0;
            for (int j = 0; j < nCycles; j++) {
                int idx = j * m + i;
                if (idx < y.size()) {
                    avg += y.get(idx);
                    count++;
                }
            }
            seasonals.add(count > 0 ? avg / count : 1.0);
        }

        // Normalizar para que sumen m (evitar deriva)
        double total = seasonals.stream().mapToDouble(Double::doubleValue).sum();
        double scale = total > 0 ? m / total : 1.0;
        for (int i = 0; i < seasonals.size(); i++) {
            seasonals.set(i, seasonals.get(i) * scale);
        }
        return seasonals;
    }

    // -------------------------------------------------------------------------
    // Filtrado de anomalías (Z-score)
    // -------------------------------------------------------------------------

    /**
     * Elimina las observaciones cuyo Z-score supera {@value #Z_SCORE_THRESHOLD}.
     */
    List<Double> filterAnomalies(List<Double> observations) {
        if (observations.size() < 3) {
            return new ArrayList<>(observations);
        }
        double mean = simpleMean(observations);
        double std = standardDeviation(observations, mean);
        if (std == 0.0) {
            return new ArrayList<>(observations);
        }
        List<Double> result = new ArrayList<>();
        for (double v : observations) {
            if (Math.abs((v - mean) / std) <= Z_SCORE_THRESHOLD) {
                result.add(v);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Utilidades estadísticas
    // -------------------------------------------------------------------------

    private double simpleMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double standardDeviation(List<Double> values, double mean) {
        double variance = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }
}
