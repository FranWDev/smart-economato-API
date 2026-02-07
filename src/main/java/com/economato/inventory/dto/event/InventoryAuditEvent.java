package com.economato.inventory.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para eventos de auditoría de inventario enviados a Kafka.
 * No contiene referencias a entidades JPA para ser serializable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAuditEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Integer productId;
    private String productName;
    private Integer userId;
    private String userName;
    private String movementType;
    private BigDecimal quantity;
    private String actionDescription;
    private String previousState;
    private String newState;
    private LocalDateTime movementDate;
}
