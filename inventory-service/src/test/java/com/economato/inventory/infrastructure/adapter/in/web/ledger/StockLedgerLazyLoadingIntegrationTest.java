package com.economato.inventory.infrastructure.adapter.in.web.ledger;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;

import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
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

        StockLedger ledger = StockLedger.builder()
                .product(product)
                .quantityDelta(new BigDecimal("10.000"))
                .resultingStock(new BigDecimal("110.000"))
                .movementType(MovementType.ENTRADA)
                .description("Movimiento prueba lazy user")
                .previousHash("0")
                .currentHash("hash" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 28))
                .transactionTimestamp(LocalDateTime.now())
                .user(adminUser)
                .sequenceNumber(1L)
                .verified(true)
                .build();
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
