package com.economato.inventory.application.usecase.mcp.mcp;

import com.economato.inventory.application.dto.ledger.mcp.McpLedgerEntryDto;
import com.economato.inventory.application.dto.crisis.mcp.McpCrisisDto;
import com.economato.inventory.application.dto.crisis.mcp.McpCrisisProductDto;
import com.economato.inventory.domain.model.crisis.FoodCrisis;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.crisis.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpLedgerReader {

    private final StockLedgerRepository stockLedgerRepository;
    private final FoodCrisisRepository foodCrisisRepository;

    public List<McpLedgerEntryDto> getProductLedger(Integer productId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return stockLedgerRepository.findByProductId(
                        productId,
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "transactionTimestamp"))
                )
                .getContent()
                .stream()
                .map(this::mapLedger)
                .toList();
    }

    public List<McpCrisisDto> getActiveCrises() {
        return foodCrisisRepository.findByStatusWithDetails(FoodCrisis.CrisisStatus.ACTIVE).stream()
                .map(crisis -> new McpCrisisDto(
                        crisis.getId(),
                        crisis.getCrisisCode(),
                        crisis.getReason(),
                        crisis.getSupplier() != null ? crisis.getSupplier().getName() : null,
                        crisis.getStatus() != null ? crisis.getStatus().name() : null,
                        crisis.getDateFrom(),
                        crisis.getDateTo(),
                        crisis.getAffectedProducts() == null ? List.of() : crisis.getAffectedProducts().stream()
                                .map(ap -> new McpCrisisProductDto(
                                        ap.getProduct() != null ? ap.getProduct().getId() : null,
                                        ap.getProduct() != null ? ap.getProduct().getName() : null,
                                        ap.getOriginalAvailabilityPercentage()
                                ))
                                .toList()
                ))
                .toList();
    }

    private McpLedgerEntryDto mapLedger(StockLedger tx) {
        return new McpLedgerEntryDto(
                tx.getId(),
                tx.getMovementType().name(),
                tx.getQuantityDelta(),
                tx.getResultingStock(),
                tx.getDescription(),
                tx.getTransactionTimestamp(),
                tx.getUser() != null ? tx.getUser().getName() : null
        );
    }
}
