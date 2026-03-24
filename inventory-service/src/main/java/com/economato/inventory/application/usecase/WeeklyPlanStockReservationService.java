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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyPlanStockReservationService {

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final ProductRepository productRepository;
    private final I18nService i18nService;

    public Map<Integer, BigDecimal> calculateReservedStock() {
        List<Object[]> results = weeklyPlanRepository.calculateReservedStock();
        Map<Integer, BigDecimal> reservedMap = new HashMap<>();
        
        for (Object[] row : results) {
            Integer productId = (Integer) row[0];
            BigDecimal quantity = (BigDecimal) row[1];
            reservedMap.put(productId, quantity != null ? quantity : BigDecimal.ZERO);
        }
        
        return reservedMap;
    }

    public BigDecimal getReservedStockForProduct(Integer productId) {
        return calculateReservedStock().getOrDefault(productId, BigDecimal.ZERO);
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

    public void validateStockForPlanActivation(Long planId) {
        List<Object[]> requiredResults = weeklyPlanRepository.calculateRequiredStockForPlan(planId);
        
        Map<Integer, BigDecimal> requiredStockMap = new HashMap<>();
        for (Object[] row : requiredResults) {
            requiredStockMap.put((Integer) row[0], (BigDecimal) row[1]);
        }
        
        if (requiredStockMap.isEmpty()) return;

        Map<Integer, BigDecimal> currentReservations = calculateReservedStock();
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
}
