package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.LoginRequestDTO;
import com.economato.inventory.application.dto.response.LoginResponseDTO;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockLedgerLazyLoadingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockLedgerRepository stockLedgerRepository;

    @MockitoBean
    private AuditEventProducer auditEventProducer;

    private String jwtToken;
    private Integer productId;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();

        User adminUser = userRepository.saveAndFlush(TestDataUtil.createAdminUser());

        LoginRequestDTO loginRequest = new LoginRequestDTO(adminUser.getUser(), "admin123");
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
        jwtToken = loginResponse.getToken();

        Product product = new Product();
        product.setName("Producto Lazy User");
        product.setUnit("KG");
        product.setUnitPrice(new BigDecimal("15.00"));
        product.setProductCode("LAZY-USER-001");
        product.setCurrentStock(new BigDecimal("100.000"));
        product = productRepository.saveAndFlush(product);
        productId = product.getId();

        StockLedger ledger = new StockLedger();
        ledger.setProduct(product);
        ledger.setQuantityDelta(new BigDecimal("10.000"));
        ledger.setResultingStock(new BigDecimal("110.000"));
        ledger.setMovementType(MovementType.ENTRADA);
        ledger.setDescription("Movimiento prueba lazy user");
        ledger.setPreviousHash("0");
        ledger.setCurrentHash("hash" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 28));
        ledger.setTransactionTimestamp(LocalDateTime.now());
        ledger.setUser(adminUser);
        ledger.setSequenceNumber(1L);
        ledger.setVerified(true);
        stockLedgerRepository.saveAndFlush(ledger);

        entityManager.clear();
    }

    @Test
    void getProductHistory_ShouldMapUserAndProductWithoutLazyInitializationException() throws Exception {
        mockMvc.perform(get("/api/stock-ledger/history/{productId}", productId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].productId").value(productId))
            .andExpect(jsonPath("$.content[0].productName").value("Producto Lazy User"))
            .andExpect(jsonPath("$.content[0].userName").value("Admin"));
    }
}
