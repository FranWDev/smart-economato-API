package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.domain.model.*;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.InventoryAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.TestDataUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryAuditFilteringIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InventoryAuditRepository inventoryAuditRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        inventoryAuditRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        User admin = TestDataUtil.createAdminUser();
        userRepository.saveAndFlush(admin);

        Product p1 = productRepository.save(TestDataUtil.createProduct("Harina de Trigo", "Ingrediente", "KG", new BigDecimal("1.0"), "H01", new BigDecimal("10")));
        Product p2 = productRepository.save(TestDataUtil.createProduct("Azúcar Blanca", "Ingrediente", "KG", new BigDecimal("2.0"), "A01", new BigDecimal("20")));

        // Audits - We use a small hack to set the dates since @CreatedDate is used
        // However, let's see if we can just save them and they get the current date,
        // and for the "range" test we use current +/- some time.
        
        InventoryAudit a1 = new InventoryAudit();
        a1.setProduct(p1);
        a1.setMovementType("ENTRADA");
        a1.setQuantity(new BigDecimal("5"));
        a1.setUser(admin);
        a1.setActionDescription("Entrada inicial");
        inventoryAuditRepository.save(a1);

        InventoryAudit a2 = new InventoryAudit();
        a2.setProduct(p2);
        a2.setMovementType("SALIDA");
        a2.setQuantity(new BigDecimal("10"));
        a2.setUser(admin);
        a2.setActionDescription("Venta");
        inventoryAuditRepository.save(a2);

        InventoryAudit a3 = new InventoryAudit();
        a3.setProduct(p1);
        a3.setMovementType("AJUSTE");
        a3.setQuantity(new BigDecimal("1"));
        a3.setUser(admin);
        a3.setActionDescription("Merma");
        inventoryAuditRepository.save(a3);
        
        inventoryAuditRepository.flush();
    }

    @Test
    void getFiltered_ByProductName_ShouldReturnCorrectAudits() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/inventory-audits")
                .header("Authorization", "Bearer " + token)
                .param("productName", "Harina")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].productName").value("Harina de Trigo"))
                .andExpect(jsonPath("$.content[1].productName").value("Harina de Trigo"));
    }

    @Test
    void getFiltered_ByType_ShouldReturnCorrectAudits() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/inventory-audits")
                .header("Authorization", "Bearer " + token)
                .param("type", "SALIDA")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].movementType").value("SALIDA"));
    }

    @Test
    void getFiltered_ByMultipleParams_ShouldReturnCorrectAudits() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/inventory-audits")
                .header("Authorization", "Bearer " + token)
                .param("productName", "Harina")
                .param("type", "AJUSTE")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].movementType").value("AJUSTE"))
                .andExpect(jsonPath("$.content[0].productName").value("Harina de Trigo"));
    }

    @Test
    void getFiltered_ByDateRange_ShouldReturnCorrectAudits() throws Exception {
        String token = loginAsAdmin();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusMinutes(1);
        LocalDateTime end = now.plusMinutes(1);

        mockMvc.perform(get("/api/inventory-audits")
                .header("Authorization", "Bearer " + token)
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));
    }
}
