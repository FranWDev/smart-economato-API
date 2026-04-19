package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.ReportRange;
import com.economato.inventory.application.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.application.dto.response.ProductStatDTO;
import com.economato.inventory.application.dto.response.RecipeStatDTO;
import com.economato.inventory.application.dto.response.UserStatDTO;
import com.economato.inventory.application.mapper.KitchenReportMapper;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.RecipeCookingAudit;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class KitchenReportService {

    private final I18nService i18nService;
    private final RecipeCookingAuditRepository auditRepository;
    private final ProductRepository productRepository;
    private final KitchenReportMapper mapper;
    private final ObjectMapper objectMapper;

    public KitchenReportService(I18nService i18nService, RecipeCookingAuditRepository auditRepository, ProductRepository productRepository, KitchenReportMapper mapper) {
        this.i18nService = i18nService;
        this.auditRepository = auditRepository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.objectMapper = new ObjectMapper();
    }

    @Cacheable(
            value = "kitchen_report",
            key = "#range.name() + '-' + (#startDate != null ? #startDate.toString() : 'null') + '-' + (#endDate != null ? #endDate.toString() : 'null')",
            unless = "#range.name() == 'DAILY'")
    @Transactional(readOnly = true)
    public KitchenReportResponseDTO generateReport(ReportRange range, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start;
        LocalDateTime end;

        LocalDateTime now = LocalDateTime.now();

        switch (range) {
            case DAILY:
                start = now.with(LocalTime.MIN);
                end = now.with(LocalTime.MAX);
                break;
            case WEEKLY:
                start = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(LocalTime.MIN);
                end = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(LocalTime.MAX);
                break;
            case MONTHLY:
                start = now.with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN);
                end = now.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
                break;
            case YEARLY:
                start = now.with(TemporalAdjusters.firstDayOfYear()).with(LocalTime.MIN);
                end = now.with(TemporalAdjusters.lastDayOfYear()).with(LocalTime.MAX);
                break;
            case CUSTOM:
                if (startDate == null || endDate == null) {
                    throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_REPORT_CUSTOM_DATES_REQUIRED));
                }
                start = startDate.atStartOfDay();
                end = endDate.atTime(LocalTime.MAX);
                break;
            case ALL_TIME:
            default:
                start = LocalDateTime.of(2000, 1, 1, 0, 0);
                end = now.with(LocalTime.MAX);
                break;
        }

        String reportPeriodText;
        if (range == ReportRange.ALL_TIME) {
            reportPeriodText = i18nService.getMessage(MessageKey.REPORT_PERIOD_ALL_TIME);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            reportPeriodText = start.toLocalDate().format(formatter) + " - " + end.toLocalDate().format(formatter);
        }

        try (Stream<RecipeCookingAudit> auditStream = (range == ReportRange.ALL_TIME) ?
                auditRepository.streamAllOrderByDateDesc() :
                auditRepository.streamByDateRange(start, end)) {

            return processAudits(auditStream, reportPeriodText);
        }
    }

    private KitchenReportResponseDTO processAudits(Stream<RecipeCookingAudit> auditStream, String reportPeriodText) {
        final BigDecimal[] totalPortionsHolder = new BigDecimal[]{BigDecimal.ZERO};
        final int[] totalSessionsHolder = new int[]{0};
        
        final BigDecimal[] totalSalesHolder = new BigDecimal[]{BigDecimal.ZERO};
        final BigDecimal[] totalWasteCostHolder = new BigDecimal[]{BigDecimal.ZERO};

        Map<Integer, RecipeStatDTO> recipeStats = new HashMap<>();
        Map<Integer, UserStatDTO> userStats = new HashMap<>();
        Map<Integer, ProductStatDTO> productStats = new HashMap<>();

        auditStream.forEach(audit -> {
            totalSessionsHolder[0]++;
            BigDecimal quantityCooked = audit.getQuantityCooked() != null ? audit.getQuantityCooked() : BigDecimal.ONE;
            totalPortionsHolder[0] = totalPortionsHolder[0].add(quantityCooked);

            if (audit.getRecipe() != null) {
                Integer recipeId = audit.getRecipe().getId();
                RecipeStatDTO rStat = recipeStats.getOrDefault(recipeId, RecipeStatDTO.builder()
                        .recipeId(recipeId)
                        .recipeName(audit.getRecipe().getName())
                        .timesCooked(0)
                        .totalQuantityCooked(BigDecimal.ZERO)
                        .build());

                rStat.setTimesCooked(rStat.getTimesCooked() + 1);
                rStat.setTotalQuantityCooked(rStat.getTotalQuantityCooked().add(quantityCooked));
                recipeStats.put(recipeId, rStat);
                
                // Financial aggregation from audit root if available (newer audits) or current formula
                BigDecimal sellingPrice = audit.getSellingPrice();
                if (sellingPrice == null) {
                    sellingPrice = (audit.getRecipe() != null && audit.getRecipe().getSellingPrice() != null) 
                                   ? audit.getRecipe().getSellingPrice() 
                                   : BigDecimal.ZERO;
                }
                
                totalSalesHolder[0] = totalSalesHolder[0].add(sellingPrice.multiply(quantityCooked));

            }

            if (audit.getUser() != null) {
                Integer userId = audit.getUser().getId();
                String nameToUse = audit.getUser().getName();
                if (nameToUse == null || nameToUse.trim().isEmpty()) {
                    nameToUse = audit.getUser().getUser();
                }

                UserStatDTO uStat = userStats.getOrDefault(userId, UserStatDTO.builder()
                        .userId(userId)
                        .userName(nameToUse)
                        .timesCooked(0)
                        .build());
                uStat.setTimesCooked(uStat.getTimesCooked() + 1);
                userStats.put(userId, uStat);
            }

            String componentsState = audit.getComponentsState();
            if (componentsState != null && !componentsState.isEmpty() && !componentsState.equals("{}")) {
                try {
                    Map<String, Object> stateMap = objectMapper.readValue(componentsState, new TypeReference<Map<String, Object>>() {});
                    if (stateMap.containsKey("components")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> components = (List<Map<String, Object>>) stateMap.get("components");
                        for (Map<String, Object> comp : components) {
                            Integer prodId = (Integer) comp.get("productId");
                            String prodName = (String) comp.get("productName");
                            Object quantObj = comp.get("quantity");
                            BigDecimal baseQuantity = BigDecimal.ZERO;
                            if (quantObj instanceof Number) {
                                baseQuantity = new BigDecimal(quantObj.toString());
                            }

                            BigDecimal usedQuantity = baseQuantity.multiply(quantityCooked);

                            ProductStatDTO pStat = productStats.getOrDefault(prodId, ProductStatDTO.builder()
                                    .productId(prodId)
                                    .productName(prodName)
                                    .totalQuantityUsed(BigDecimal.ZERO)
                                    .estimatedCost(BigDecimal.ZERO)
                                    .build());

                            pStat.setTotalQuantityUsed(pStat.getTotalQuantityUsed().add(usedQuantity));
                            productStats.put(prodId, pStat);
                            
                            // Merma calculation using snapshot data from audit
                            BigDecimal unitPrice = BigDecimal.ZERO;
                            Object upObj = comp.get("unitPrice");
                            if (upObj instanceof Number) unitPrice = new BigDecimal(upObj.toString());
                            
                            BigDecimal availability = new BigDecimal("100");
                            Object apObj = comp.get("availabilityPercentage");
                            if (apObj instanceof Number) availability = new BigDecimal(apObj.toString());
                            
                            if (availability.compareTo(BigDecimal.ZERO) > 0 && availability.compareTo(new BigDecimal("100")) < 0) {
                                BigDecimal grossQty = usedQuantity.multiply(new BigDecimal("100")).divide(availability, 10, RoundingMode.HALF_UP);
                                BigDecimal wasteQty = grossQty.subtract(usedQuantity);
                                totalWasteCostHolder[0] = totalWasteCostHolder[0].add(wasteQty.multiply(unitPrice));
                            }
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Error parsing components state for audit ID {}: {}", audit.getId(), e.getMessage());
                }
            }
        });

        if (totalSessionsHolder[0] == 0) {
            return mapper.toReport(
                reportPeriodText, 0, BigDecimal.ZERO, 0, 0, 0, 
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
            );
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        List<Product> products = productRepository.findAllById(productStats.keySet());
        Map<Integer, BigDecimal> productPrices = products.stream()
                .collect(Collectors.toMap(Product::getId, Product::getUnitPrice));
        Map<Integer, String> productUnits = products.stream()
                .collect(Collectors.toMap(Product::getId, Product::getUnit));

        for (ProductStatDTO pStat : productStats.values()) {
            BigDecimal price = productPrices.getOrDefault(pStat.getProductId(), BigDecimal.ZERO);
            String unit = productUnits.getOrDefault(pStat.getProductId(), "UND");
            BigDecimal costForProduct = pStat.getTotalQuantityUsed().multiply(price);
            pStat.setEstimatedCost(costForProduct);
            pStat.setUnit(unit);
            totalCost = totalCost.add(costForProduct);
        }

        BigDecimal totalSales = totalSalesHolder[0].setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalWasteCost = totalWasteCostHolder[0].setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossProfit = totalSales.subtract(totalCost.subtract(totalWasteCost)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netProfit = totalSales.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);

        return mapper.toReport(
                reportPeriodText,
                totalSessionsHolder[0],
                totalPortionsHolder[0],
                recipeStats.size(),
                userStats.size(),
                productStats.size(),
                totalCost.setScale(2, RoundingMode.HALF_UP),
                totalWasteCost,
                totalSales,
                grossProfit,
                netProfit,
                recipeStats.values().stream().sorted(Comparator.comparing(RecipeStatDTO::getTimesCooked).reversed()).collect(Collectors.toList()),
                userStats.values().stream().sorted(Comparator.comparing(UserStatDTO::getTimesCooked).reversed()).collect(Collectors.toList()),
                productStats.values().stream().sorted(Comparator.comparing(ProductStatDTO::getTotalQuantityUsed).reversed()).collect(Collectors.toList())
        );
    }
}
