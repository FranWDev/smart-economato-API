package com.economato.inventory.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ledger_block", indexes = {
        @Index(name = "idx_block_number", columnList = "block_number"),
        @Index(name = "idx_block_hash", columnList = "block_hash")
})
public class LedgerBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "block_number", nullable = false, unique = true)
    private Long blockNumber;

    @NotBlank
    @Size(max = 64)
    @Column(name = "previous_block_hash", nullable = false, length = 64)
    private String previousBlockHash;

    @NotBlank
    @Size(max = 64)
    @Column(name = "merkle_root", nullable = false, length = 64)
    private String merkleRoot;

    @NotBlank
    @Size(max = 64)
    @Column(name = "block_hash", nullable = false, unique = true, length = 64)
    private String blockHash;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @NotNull
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;

    @Builder.Default
    @NotNull
    @Column(name = "hmac_key_version", nullable = false)
    private Integer hmacKeyVersion = 1;

    @OneToMany(mappedBy = "block", fetch = FetchType.LAZY)
    private List<StockLedger> transactions;
}
