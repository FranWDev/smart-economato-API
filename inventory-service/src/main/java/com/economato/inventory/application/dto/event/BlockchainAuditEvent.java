package com.economato.inventory.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockchainAuditEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long blockNumber;
    private String blockHash;
    private String previousBlockHash;
    private String merkleRoot;
    private Long nonce;
    private Integer difficulty;
    private Integer transactionCount;
    private Integer hmacKeyVersion;
    private LocalDateTime timestamp;
    private List<String> transactionHashes;
}
