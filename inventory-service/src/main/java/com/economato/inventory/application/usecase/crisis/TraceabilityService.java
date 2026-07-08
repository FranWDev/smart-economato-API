package com.economato.inventory.application.usecase.crisis;

import com.economato.inventory.application.dto.crisis.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.crisis.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.crisis.response.CrisisResponseDTO;
import com.economato.inventory.application.dto.crisis.response.ForwardTraceabilityDTO;
import com.economato.inventory.application.dto.crisis.response.ReverseTraceabilityDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Facade y orquestador del módulo de trazabilidad.
 * Delega sus responsabilidades a sub-servicios cohesivos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TraceabilityService {

    private final CrisisContainmentService crisisContainmentService;
    private final TraceabilityQueryService traceabilityQueryService;

    @Caching(evict = {
        @CacheEvict(value = "products_page", allEntries = true),
        @CacheEvict(value = "product", allEntries = true),
        @CacheEvict(value = "products_search", allEntries = true),
        @CacheEvict(value = "cookable_recipes", allEntries = true),
        @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
        @CacheEvict(value = "stock_alerts", allEntries = true)
    })
    @RealtimeSync(entityType = "crisis", action = "CREATE",
            affectedDomains = {"crisis", "product", "stock_alerts", "weekly_plan"})
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public CrisisResponseDTO activateCrisis(CrisisActivationRequestDTO request) {
        return crisisContainmentService.activateCrisis(request);
    }

    @Caching(evict = {
        @CacheEvict(value = "products_page", allEntries = true),
        @CacheEvict(value = "product", allEntries = true),
        @CacheEvict(value = "products_search", allEntries = true),
        @CacheEvict(value = "cookable_recipes", allEntries = true),
        @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
        @CacheEvict(value = "stock_alerts", allEntries = true)
    })
    @RealtimeSync(entityType = "crisis", action = "UPDATE",
            affectedDomains = {"crisis", "product", "stock_alerts", "weekly_plan"})
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void liftCrisis(CrisisLiftRequestDTO request) {
        crisisContainmentService.liftCrisis(request);
    }

    @Transactional(readOnly = true)
    public List<CrisisResponseDTO> getAllCrises() {
        return crisisContainmentService.getAllCrises();
    }

    @Transactional(readOnly = true)
    public Page<CrisisResponseDTO> getCrisisHistory(String search, Pageable pageable) {
        return crisisContainmentService.getCrisisHistory(search, pageable);
    }

    @Transactional(readOnly = true)
    public CrisisResponseDTO getCrisisById(Long crisisId) {
        return crisisContainmentService.getCrisisById(crisisId);
    }

    @Transactional(readOnly = true)
    public ForwardTraceabilityDTO getForwardTraceability(Integer supplierId, List<Integer> productIds,
                                                         LocalDateTime from, LocalDateTime to) {
        return traceabilityQueryService.getForwardTraceability(supplierId, productIds, from, to);
    }

    @Transactional(readOnly = true)
    public ReverseTraceabilityDTO getReverseTraceability(Long cookingAuditId) {
        return traceabilityQueryService.getReverseTraceability(cookingAuditId);
    }

    @Transactional(readOnly = true)
    public List<RecipeCookingAuditResponseDTO> getCookingAuditsByBatchId(Long batchId) {
        return traceabilityQueryService.getCookingAuditsByBatchId(batchId);
    }
}