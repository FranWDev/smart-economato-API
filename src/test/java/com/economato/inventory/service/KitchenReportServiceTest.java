package com.economato.inventory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.dto.request.ReportRange;
import com.economato.inventory.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.dto.response.ProductStatDTO;
import com.economato.inventory.dto.response.RecipeStatDTO;
import com.economato.inventory.dto.response.UserStatDTO;
import com.economato.inventory.mapper.KitchenReportMapper;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeCookingAudit;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeCookingAuditRepository;

@ExtendWith(MockitoExtension.class)
class KitchenReportServiceTest {

    @Mock
    private RecipeCookingAuditRepository auditRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private KitchenReportMapper mapper; // Use real mapper to get the DTO assembled properly

    @InjectMocks
    private KitchenReportService kitchenReportService;

    @Test
    void testGenerateReport_emptyAudits() {
        when(auditRepository.streamByDateRange(any(), any())).thenReturn(Stream.empty());

        KitchenReportResponseDTO report = kitchenReportService.generateReport(ReportRange.DAILY, null, null);

        assertNotNull(report);
        assertNotNull(report.getReportPeriod(), "Report period should not be null");
        assertEquals(0, report.getTotalCookingSessions());
        assertEquals(0, report.getDistinctRecipesCooked());
    }

    @Test
    void testGenerateReport_withRealMetrics() {
        Recipe recipe1 = new Recipe();
        recipe1.setId(10);
        recipe1.setName("Pizza Margarita");
        User user1 = new User();
        user1.setId(5);
        user1.setName("Chef Juan");

        Product cheese = new Product();
        cheese.setId(100);
        cheese.setName("Queso Mozzarella");
        cheese.setUnit("KG");
        cheese.setUnitPrice(BigDecimal.valueOf(5.50));

        Product tomato = new Product();
        tomato.setId(200);
        tomato.setName("Tomate Frito");
        tomato.setUnit("L");
        tomato.setUnitPrice(BigDecimal.valueOf(2.00));

        String componentsJson = """
                {
                   "components": [
                      {"productId": 100, "productName": "Queso Mozzarella", "quantity": 1.0},
                      {"productId": 200, "productName": "Tomate Frito", "quantity": 0.5}
                   ]
                }
                """;

        RecipeCookingAudit audit = new RecipeCookingAudit();
        audit.setId(1L);
        audit.setRecipe(recipe1);
        audit.setUser(user1);
        audit.setQuantityCooked(BigDecimal.valueOf(3));
        audit.setComponentsState(componentsJson);

        when(auditRepository.streamByDateRange(any(), any())).thenReturn(Stream.of(audit));

        when(productRepository.findAllById(any(Set.class))).thenReturn(List.of(cheese, tomato));

        KitchenReportResponseDTO report = kitchenReportService.generateReport(ReportRange.WEEKLY, null, null);

        assertNotNull(report);
        assertNotNull(report.getReportPeriod(), "Report period should not be null");

        assertEquals(1, report.getTotalCookingSessions(), "There should be 1 cooking session total");
        assertEquals(BigDecimal.valueOf(3), report.getTotalPortionsCooked(), "Total portions should sum to 3");
        assertEquals(1, report.getDistinctRecipesCooked());
        assertEquals(1, report.getDistinctUsersCooking());
        assertEquals(2, report.getDistinctProductsUsed());

        assertEquals(0, BigDecimal.valueOf(19.50).compareTo(report.getTotalEstimatedCost()),
                "Cost calculation mismatched");

        List<RecipeStatDTO> recipes = report.getTopRecipes();
        assertEquals(1, recipes.size());
        assertEquals(10, recipes.get(0).getRecipeId());
        assertEquals(1, recipes.get(0).getTimesCooked());
        assertEquals(BigDecimal.valueOf(3), recipes.get(0).getTotalQuantityCooked());

        List<UserStatDTO> users = report.getTopUsers();
        assertEquals(1, users.size());
        assertEquals(5, users.get(0).getUserId());
        assertEquals("Chef Juan", users.get(0).getUserName());

        List<ProductStatDTO> products = report.getTopProducts();
        assertEquals(2, products.size());

        ProductStatDTO cheeseStat = products.stream().filter(p -> p.getProductId() == 100).findFirst().orElseThrow();
        assertEquals("Queso Mozzarella", cheeseStat.getProductName());
        assertEquals("KG", cheeseStat.getUnit());
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(cheeseStat.getTotalQuantityUsed()));
        assertEquals(0, BigDecimal.valueOf(16.50).compareTo(cheeseStat.getEstimatedCost()));

        ProductStatDTO tomatoStat = products.stream().filter(p -> p.getProductId() == 200).findFirst().orElseThrow();
        assertEquals("Tomate Frito", tomatoStat.getProductName());
        assertEquals("L", tomatoStat.getUnit());
        assertEquals(0, BigDecimal.valueOf(1.5).compareTo(tomatoStat.getTotalQuantityUsed()));
        assertEquals(0, BigDecimal.valueOf(3.00).compareTo(tomatoStat.getEstimatedCost()));
    }
}
