package com.economato.inventory.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Event DTO for stock-prediction-events topic.
 * Unified event for all stock modifications that trigger a prediction refresh.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPredictionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String triggerType;
    private List<Integer> affectedProductIds;
    private Map<Integer, List<DailyConsumption>> productHistories;
    private LocalDateTime timestamp;
    private Integer userId;
    private String userName;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyConsumption implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDate date;
        private BigDecimal consumed;
    }
}
