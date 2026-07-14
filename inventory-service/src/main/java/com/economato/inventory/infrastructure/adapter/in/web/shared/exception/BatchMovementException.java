package com.economato.inventory.infrastructure.adapter.in.web.shared.exception;

import com.economato.inventory.application.dto.stock.response.BatchStockMovementResponseDTO;
import lombok.Getter;

@Getter
public class BatchMovementException extends RuntimeException {
    private final BatchStockMovementResponseDTO errorResponse;

    public BatchMovementException(BatchStockMovementResponseDTO errorResponse, String message) {
        super(message);
        this.errorResponse = errorResponse;
    }
}
