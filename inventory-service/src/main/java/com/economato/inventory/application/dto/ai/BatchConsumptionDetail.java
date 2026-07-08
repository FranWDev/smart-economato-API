package com.economato.inventory.application.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BatchConsumptionDetail {
    private final Long batchId;
    private final BigDecimal quantityConsumed;
}
