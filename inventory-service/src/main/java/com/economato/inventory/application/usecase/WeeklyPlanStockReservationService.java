package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.WeeklyPlan;
import com.economato.inventory.domain.model.WeeklyPlanSlot;
import com.economato.inventory.domain.model.RecipeComponent;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.WeeklyPlanRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyPlanStockReservationService {

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final ProductRepository productRepository;
    private final I18nService i18nService;

    public Map<Integer, BigDecimal> calculateReservedStock(Long excludePlanId) {
        List<Object[]> results = weeklyPlanRepository.calculateReservedStock(excludePlanId);
        Map<Integer, BigDecimal> reservedMap = new HashMap<>();
        
        for (Object[] row : results) {
            Integer productId = (Integer) row[0];
            BigDecimal quantity = toBigDecimal(row[1]);
            reservedMap.put(productId, quantity != null ? quantity : BigDecimal.ZERO);
        }
        
        return reservedMap;
    }

    public BigDecimal getReservedStockForProduct(Integer productId) {
        return calculateReservedStock(null).getOrDefault(productId, BigDecimal.ZERO);
    }

    public boolean isStockSufficientForAction(Integer productId, BigDecimal requiredAmount) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND, new Object[]{productId})));
            
        BigDecimal currentStock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
        BigDecimal availabilityPct = product.getAvailabilityPercentage() != null ? product.getAvailabilityPercentage() : new BigDecimal("100.00");
        
        BigDecimal availableForUse = currentStock.multiply(availabilityPct).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
        BigDecimal reservedStock = getReservedStockForProduct(productId);
        
        BigDecimal actualAvailable = availableForUse.subtract(reservedStock);
        
        return actualAvailable.compareTo(requiredAmount) >= 0;
    }

    public void validateDecrementAgainstActiveReservations(Integer productId, BigDecimal decrementAmount, BigDecimal currentStock) {
        if (decrementAmount == null || decrementAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal reservedStock = getReservedStockForProduct(productId);
        BigDecimal resultingStock = (currentStock != null ? currentStock : BigDecimal.ZERO).subtract(decrementAmount);

        if (resultingStock.compareTo(reservedStock) < 0) {
            throw new InvalidOperationException(i18nService.getMessage(
                    MessageKey.ERROR_WEEKLY_PLAN_AVAILABILITY_VIOLATION,
                    new Object[] { decrementAmount, reservedStock, resultingStock }));
        }
    }

    public void validateStockForPlanActivation(Long planId) {
        List<Object[]> requiredResults = weeklyPlanRepository.calculateRequiredStockForPlan(planId);
        
        Map<Integer, BigDecimal> requiredStockMap = new HashMap<>();
        for (Object[] row : requiredResults) {
            requiredStockMap.put((Integer) row[0], toBigDecimal(row[1]));
        }
        
        if (requiredStockMap.isEmpty()) return;

        Map<Integer, BigDecimal> currentReservations = calculateReservedStock(planId);
        List<Product> products = productRepository.findAllById(requiredStockMap.keySet());
        Map<Integer, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        
        List<String> missingProducts = new ArrayList<>();
        
        for (Map.Entry<Integer, BigDecimal> entry : requiredStockMap.entrySet()) {
            Integer productId = entry.getKey();
            BigDecimal needed = entry.getValue();
            
            Product product = productMap.get(productId);
            if (product == null) continue;

            BigDecimal currentStock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
            BigDecimal availabilityPct = product.getAvailabilityPercentage() != null ? product.getAvailabilityPercentage() : new BigDecimal("100.00");
            
            BigDecimal maxAvailable = currentStock.multiply(availabilityPct).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
            BigDecimal alreadyReserved = currentReservations.getOrDefault(productId, BigDecimal.ZERO);
            
            BigDecimal trulyAvailable = maxAvailable.subtract(alreadyReserved);
            
            if (trulyAvailable.compareTo(needed) < 0) {
                missingProducts.add(product.getName() + " (Faltan: " + needed.subtract(trulyAvailable).setScale(2, RoundingMode.HALF_UP) + " " + product.getUnit() + ")");
            }
        }
        
        if (!missingProducts.isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_INSUFFICIENT_STOCK, new Object[]{String.join(", ", missingProducts)}));
        }
    }

    public void validateStockForPlanUpdate(WeeklyPlan existingPlan, List<WeeklyPlanSlot> requestedSlots) {
        // 1. Calcular lo que requeriría el plan con los nuevos slots propuestos
        Map<Integer, BigDecimal> proposedRequirements = new HashMap<>();
        
        // El plan resultante tendrá los CONFIRMED actuales + los nuevos unconfirmed (requestedSlots que no coinciden con confirmed)
        List<WeeklyPlanSlot> finalSlots = new ArrayList<>();
        
        // Añadir CONFIRMED existentes
        for (WeeklyPlanSlot s : existingPlan.getSlots()) {
            if (s.getStatus() == com.economato.inventory.domain.model.WeeklyPlanSlotStatus.CONFIRMED) {
                finalSlots.add(s);
            }
        }
        
        // Añadir nuevos de la petición que no sean duplicados de los confirmados
        Set<String> confirmedKeys = finalSlots.stream()
                .map(s -> s.getRecipe().getId() + "-" + s.getDayOfWeek() + "-" + s.getStartTime() + "-" + s.getEndTime())
                .collect(Collectors.toSet());
                
        for (WeeklyPlanSlot s : requestedSlots) {
            String key = s.getRecipe().getId() + "-" + s.getDayOfWeek() + "-" + s.getStartTime() + "-" + s.getEndTime();
            if (!confirmedKeys.contains(key)) {
                finalSlots.add(s);
            }
        }

        for (WeeklyPlanSlot slot : finalSlots) {
            // Solo reservan stock los PENDING o IN_PROGRESS. Los CONFIRMED ya salieron del stock (salida registrada)
            if (slot.getStatus() == com.economato.inventory.domain.model.WeeklyPlanSlotStatus.PENDING || 
                slot.getStatus() == com.economato.inventory.domain.model.WeeklyPlanSlotStatus.IN_PROGRESS) {
                
                for (RecipeComponent rc : slot.getRecipe().getComponents()) {
                    BigDecimal needed = rc.getQuantity().multiply(slot.getQuantity())
                            .divide(slot.getRecipe().getPortions(), 4, RoundingMode.HALF_UP);
                    proposedRequirements.merge(rc.getProduct().getId(), needed, BigDecimal::add);
                }
            }
        }
        
        if (proposedRequirements.isEmpty()) return;

        // 2. Comparar con stock disponible (excluyendo la reserva ACTUAL de este plan)
        Map<Integer, BigDecimal> currentReservationsOtherPlans = calculateReservedStock(existingPlan.getId());
        List<Product> products = productRepository.findAllById(proposedRequirements.keySet());
        Map<Integer, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        
        List<String> missingProducts = new ArrayList<>();
        
        for (Map.Entry<Integer, BigDecimal> entry : proposedRequirements.entrySet()) {
            Integer productId = entry.getKey();
            BigDecimal needed = entry.getValue();
            
            Product product = productMap.get(productId);
            if (product == null) continue;

            BigDecimal currentStock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
            BigDecimal availabilityPct = product.getAvailabilityPercentage() != null ? product.getAvailabilityPercentage() : new BigDecimal("100.00");
            
            BigDecimal maxAvailable = currentStock.multiply(availabilityPct).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
            BigDecimal alreadyReserved = currentReservationsOtherPlans.getOrDefault(productId, BigDecimal.ZERO);
            
            BigDecimal trulyAvailable = maxAvailable.subtract(alreadyReserved);
            
            if (trulyAvailable.compareTo(needed) < 0) {
                missingProducts.add(product.getName() + " (Faltan: " + needed.subtract(trulyAvailable).setScale(2, RoundingMode.HALF_UP) + " " + product.getUnit() + ")");
            }
        }
        
        if (!missingProducts.isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_INSUFFICIENT_STOCK, new Object[]{String.join(", ", missingProducts)}));
        }
    }

    public void validateAvailabilityPercentageChange(Integer productId, BigDecimal newPercentage) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND, new Object[]{productId})));
            
        BigDecimal currentStock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
        BigDecimal newMaxAvailable = currentStock.multiply(newPercentage).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
        BigDecimal reservedStock = getReservedStockForProduct(productId);
        
        if (newMaxAvailable.compareTo(reservedStock) < 0) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_WEEKLY_PLAN_AVAILABILITY_VIOLATION, new Object[]{newPercentage, reservedStock, newMaxAvailable}));
        }
    }

    public List<com.economato.inventory.application.dto.response.WeeklyPlanStockRequirementDTO> getStockRequirements(Long planId) {
        List<Object[]> requiredResults = weeklyPlanRepository.calculateRequiredStockForPlan(planId);
        Map<Integer, BigDecimal> requiredStockMap = new HashMap<>();
        for (Object[] row : requiredResults) {
            requiredStockMap.put((Integer) row[0], toBigDecimal(row[1]));
        }
        
        List<com.economato.inventory.application.dto.response.WeeklyPlanStockRequirementDTO> dtos = new ArrayList<>();
        if (requiredStockMap.isEmpty()) return dtos;

        Map<Integer, BigDecimal> currentReservations = calculateReservedStock(planId);
        List<Product> products = productRepository.findAllById(requiredStockMap.keySet());
        WeeklyPlan plan = weeklyPlanRepository.findById(planId).orElseThrow();
        
        for (Product product : products) {
            BigDecimal needed = requiredStockMap.get(product.getId());
            BigDecimal currentStock = product.getCurrentStock() != null ? product.getCurrentStock() : BigDecimal.ZERO;
            BigDecimal availabilityPct = product.getAvailabilityPercentage() != null ? product.getAvailabilityPercentage() : new BigDecimal("100.00");
            
            BigDecimal maxAvailable = currentStock.multiply(availabilityPct).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
            BigDecimal reservedByOtherPlans = currentReservations.getOrDefault(product.getId(), BigDecimal.ZERO);
            
            BigDecimal trulyAvailable = maxAvailable.subtract(reservedByOtherPlans);
            
            BigDecimal grossNeeded = availabilityPct.compareTo(BigDecimal.ZERO) > 0
                ? needed.multiply(new BigDecimal("100")).divide(availabilityPct, 3, RoundingMode.HALF_UP)
                : needed;
            
            BigDecimal grossReservedByOtherPlans = availabilityPct.compareTo(BigDecimal.ZERO) > 0
                ? reservedByOtherPlans.multiply(new BigDecimal("100")).divide(availabilityPct, 3, RoundingMode.HALF_UP)
                : reservedByOtherPlans;
            
            dtos.add(com.economato.inventory.application.dto.response.WeeklyPlanStockRequirementDTO.builder()
                .productId(product.getId())
                .productName(product.getName())
                .requiredQuantity(needed)
                .grossRequiredQuantity(grossNeeded)
                .availabilityPercentage(availabilityPct)
                .availableStock(maxAvailable)
                .grossAvailableStock(currentStock)
                .reservedByOtherPlans(reservedByOtherPlans)
                .grossReservedByOtherPlans(grossReservedByOtherPlans)
                .sufficient(trulyAvailable.compareTo(needed) >= 0)
                .build());

        }
        return dtos;
    }
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        BigDecimal bd;
        if (value instanceof BigDecimal) {
            bd = (BigDecimal) value;
        } else if (value instanceof Number) {
            bd = new BigDecimal(value.toString());
        } else {
            return BigDecimal.ZERO;
        }
        return bd.setScale(4, RoundingMode.HALF_UP);
    }
}
