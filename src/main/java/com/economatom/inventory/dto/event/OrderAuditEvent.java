package com.economatom.inventory.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO para eventos de auditoría de órdenes enviados a Kafka.
 * No contiene referencias a entidades JPA para ser serializable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAuditEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Integer orderId;
    private Integer userId;
    private String userName;
    private String action;
    private String details;
    private String previousState;
    private String newState;
    private LocalDateTime auditDate;
}
