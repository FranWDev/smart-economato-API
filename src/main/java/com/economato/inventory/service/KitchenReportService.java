package com.economato.inventory.service;

import com.economato.inventory.dto.request.ReportRange;
import com.economato.inventory.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.dto.response.ProductStatDTO;
import com.economato.inventory.dto.response.RecipeStatDTO;
import com.economato.inventory.dto.response.UserStatDTO;
import com.economato.inventory.mapper.KitchenReportMapper;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.RecipeCookingAudit;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeCookingAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class KitchenReportService {

    private final RecipeCookingAuditRepository auditRepository;
    private final ProductRepository productRepository;
    private final KitchenReportMapper mapper;
    private final ObjectMapper objectMapper;

    public KitchenReportService(RecipeCookingAuditRepository auditRepository, ProductRepository productRepository, KitchenReportMapper mapper) {
        this.auditRepository = auditRepository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.objectMapper = new ObjectMapper();
    }

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
                // Lunes a Domingo de la semana actual
                start = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(LocalTime.MIN);
                end = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(LocalTime.MAX);
                break;
            case MONTHLY:
                // Dia 1 al ultimo del mes
                start = now.with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN);
                end = now.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
                break;
            case YEARLY:
                // 1 de Enero al 31 de Diciembre
                start = now.with(TemporalAdjusters.firstDayOfYear()).with(LocalTime.MIN);
                end = now.with(TemporalAdjusters.lastDayOfYear()).with(LocalTime.MAX);
                break;
            case CUSTOM:
                if (startDate == null || endDate == null) {
                    throw new IllegalArgumentException("Para CUSTOM se requieren fechas de inicio y fin.");
                }
                start = startDate.atStartOfDay();
                end = endDate.atTime(LocalTime.MAX);
                break;
            case ALL_TIME:
            default:
                start = LocalDateTime.of(2000, 1, 1, 0, 0); // Fechas seguras en el pasado
                end = now.with(LocalTime.MAX);
                break;
        }

        try (Stream<RecipeCookingAudit> auditStream = (range == ReportRange.ALL_TIME) ? 
                auditRepository.streamAllOrderByDateDesc() : 
                auditRepository.streamByDateRange(start, end)) {
            
            return processAudits(auditStream, range.name());
        }
    }

    private KitchenReportResponseDTO processAudits(Stream<RecipeCookingAudit> auditStream, String reportPeriod) {
        
        final BigDecimal[] totalPortionsHolder = new BigDecimal[]{BigDecimal.ZERO};
        final int[] totalSessionsHolder = new int[]{0};

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
            }

            if (audit.getUser() != null) {
                Integer userId = audit.getUser().getId();
                UserStatDTO uStat = userStats.getOrDefault(userId, UserStatDTO.builder()
                        .userId(userId)
                        .userName(audit.getUser().getName())
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
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Error parsing components state for audit ID {}: {}", audit.getId(), e.getMessage());
                }
            }
        });

        if (totalSessionsHolder[0] == 0) {
            return mapper.toReport(
                reportPeriod, 0, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, 
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
            );
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        List<Product> products = productRepository.findAllById(productStats.keySet());
        Map<Integer, BigDecimal> productPrices = products.stream()
                .collect(Collectors.toMap(Product::getId, Product::getUnitPrice));

        for (ProductStatDTO pStat : productStats.values()) {
            BigDecimal price = productPrices.getOrDefault(pStat.getProductId(), BigDecimal.ZERO);
            BigDecimal costForProduct = pStat.getTotalQuantityUsed().multiply(price);
            pStat.setEstimatedCost(costForProduct);
            totalCost = totalCost.add(costForProduct);
        }

        List<RecipeStatDTO> topRecipes = recipeStats.values().stream()
                .sorted(Comparator.comparing(RecipeStatDTO::getTimesCooked).reversed())
                .collect(Collectors.toList());

        List<UserStatDTO> topUsers = userStats.values().stream()
                .sorted(Comparator.comparing(UserStatDTO::getTimesCooked).reversed())
                .collect(Collectors.toList());

        List<ProductStatDTO> topProducts = productStats.values().stream()
                .sorted(Comparator.comparing(ProductStatDTO::getTotalQuantityUsed).reversed())
                .collect(Collectors.toList());

        return mapper.toReport(
                reportPeriod,
                totalSessionsHolder[0],
                totalPortionsHolder[0],
                recipeStats.size(),
                userStats.size(),
                productStats.size(),
                totalCost,
                topRecipes,
                topUsers,
                topProducts
        );
    }
}
