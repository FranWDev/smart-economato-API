package com.economato.inventory.infrastructure.adapter.in.web.ledger;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.economato.inventory.application.dto.stock.request.BatchStockMovementRequestDTO;
import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.stock.request.StockMovementItemDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import java.time.LocalDate;

class StockLedgerBatchIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/stock-ledger/batch";
    private static final String AUTH_URL = "/api/auth/login";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockLedgerRepository stockLedgerRepository;

    @Autowired
    private ProductBatchRepository productBatchRepository;

    private Product product1;
    private Product product2;
    private Product product3;
    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();

        testUser = TestDataUtil.createAdminUser();
        testUser = userRepository.saveAndFlush(testUser);

        product1 = TestDataUtil.createFlour();
        product1 = productRepository.saveAndFlush(product1);

        product2 = TestDataUtil.createSugar();
        product2 = productRepository.saveAndFlush(product2);

        product3 = TestDataUtil.createProduct("Huevos", "UND",
                new BigDecimal("0.20"), "HUE001", new BigDecimal("200.0"));
        product3 = productRepository.saveAndFlush(product3);

        List<ProductBatch> batches = new ArrayList<>();
        batches.add(createDummyBatch(product1));
        batches.add(createDummyBatch(product2));
        batches.add(createDummyBatch(product3));
        productBatchRepository.saveAllAndFlush(batches);

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName(testUser.getName());
        loginRequest.setPassword("admin123");

        String response = mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
        jwtToken = loginResponse.getToken();
    }

    private ProductBatch createDummyBatch(Product p) {
        ProductBatch b = new ProductBatch();
        b.setProduct(p);
        b.setInitialQuantity(p.getCurrentStock());
        b.setRemainingQuantity(p.getCurrentStock());
        b.setExpirationDate(LocalDate.now().plusYears(1));
        b.setReceivedAt(java.time.LocalDateTime.now());
        b.setDepleted(false);
        return b;
    }

    @Test
    void whenBatchMovement_WithValidData_thenSuccessAndAllTransactionsCreated() throws Exception {

        BatchStockMovementRequestDTO batchRequest = new BatchStockMovementRequestDTO();
        batchRequest.setReason("Rollback de receta errónea #123");

        List<StockMovementItemDTO> movements = new ArrayList<>();

        StockMovementItemDTO movement1 = new StockMovementItemDTO();
        movement1.setProductId(product1.getId());
        movement1.setQuantityDelta(new BigDecimal("10.0"));
        movement1.setMovementType(MovementType.MODIFICACION);
        movement1.setExpirationDate(java.time.LocalDate.now().plusDays(30));
        movement1.setDescription("Devolver harina");
        movements.add(movement1);

        StockMovementItemDTO movement2 = new StockMovementItemDTO();
        movement2.setProductId(product2.getId());
        movement2.setQuantityDelta(new BigDecimal("5.0"));
        movement2.setMovementType(MovementType.MODIFICACION);
        movement2.setExpirationDate(java.time.LocalDate.now().plusDays(30));
        movement2.setDescription("Devolver azúcar");
        movements.add(movement2);

        StockMovementItemDTO movement3 = new StockMovementItemDTO();
        movement3.setProductId(product3.getId());
        movement3.setQuantityDelta(new BigDecimal("20.0"));
        movement3.setMovementType(MovementType.MODIFICACION);
        movement3.setExpirationDate(java.time.LocalDate.now().plusDays(30));
        movement3.setDescription("Devolver huevos");
        movements.add(movement3);

        batchRequest.setMovements(movements);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(asJsonString(batchRequest))
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.processedCount", is(3)))
                .andExpect(jsonPath("$.totalCount", is(3)))
                .andExpect(jsonPath("$.transactions", hasSize(3)));

        List<StockLedger> ledger1 = stockLedgerRepository.findByProductIdOrderBySequenceNumber(product1.getId());
        assertEquals(1, ledger1.size());
        assertEquals(0, new BigDecimal("10.0").compareTo(ledger1.get(0).getQuantityDelta()));

        List<StockLedger> ledger2 = stockLedgerRepository.findByProductIdOrderBySequenceNumber(product2.getId());
        assertEquals(1, ledger2.size());
        assertEquals(0, new BigDecimal("5.0").compareTo(ledger2.get(0).getQuantityDelta()));

        List<StockLedger> ledger3 = stockLedgerRepository.findByProductIdOrderBySequenceNumber(product3.getId());
        assertEquals(1, ledger3.size());
        assertEquals(0, new BigDecimal("20.0").compareTo(ledger3.get(0).getQuantityDelta()));
    }

    @Test
    void whenBatchMovement_WithInsufficientStock_thenFailsAndRevertsAll() throws Exception {

        BatchStockMovementRequestDTO batchRequest = new BatchStockMovementRequestDTO();
        batchRequest.setReason("Test transaccionalidad");

        List<StockMovementItemDTO> movements = new ArrayList<>();

        StockMovementItemDTO movement1 = new StockMovementItemDTO();
        movement1.setProductId(product1.getId());
        movement1.setQuantityDelta(new BigDecimal("10.0"));
        movement1.setMovementType(MovementType.ENTRADA);
        movement1.setExpirationDate(java.time.LocalDate.now().plusDays(30));
        movements.add(movement1);

        StockMovementItemDTO movement2 = new StockMovementItemDTO();
        movement2.setProductId(product2.getId());
        movement2.setQuantityDelta(new BigDecimal("-1000.0"));
        movement2.setMovementType(MovementType.SALIDA);
        movement2.setExpirationDate(java.time.LocalDate.now().plusDays(30));
        movements.add(movement2);

        batchRequest.setMovements(movements);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(asJsonString(batchRequest))
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.processedCount", is(0)))
                .andExpect(jsonPath("$.errorDetail", containsString("Stock insuficiente")));

        List<StockLedger> ledger1 = stockLedgerRepository.findByProductIdOrderBySequenceNumber(product1.getId());
        assertEquals(0, ledger1.size(), "No debe haber transacciones del producto 1");

        List<StockLedger> ledger2 = stockLedgerRepository.findByProductIdOrderBySequenceNumber(product2.getId());
        assertEquals(0, ledger2.size(), "No debe haber transacciones del producto 2");

        Product unchangedProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100.000").compareTo(unchangedProduct1.getCurrentStock()));
    }

    @Test
    void whenBatchMovement_WithEmptyList_thenBadRequest() throws Exception {

        BatchStockMovementRequestDTO batchRequest = new BatchStockMovementRequestDTO();
        batchRequest.setMovements(new ArrayList<>());
        batchRequest.setReason("Test lista vacía");

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(asJsonString(batchRequest))
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenBatchMovement_WithNegativeAndPositiveDeltas_thenSucceeds() throws Exception {

        BatchStockMovementRequestDTO batchRequest = new BatchStockMovementRequestDTO();
        batchRequest.setReason("Rollback de producción errónea");

        List<StockMovementItemDTO> movements = new ArrayList<>();

        StockMovementItemDTO movement1 = new StockMovementItemDTO();
        movement1.setProductId(product1.getId());
        movement1.setQuantityDelta(new BigDecimal("5.0"));
        movement1.setMovementType(MovementType.MODIFICACION);
        movement1.setExpirationDate(java.time.LocalDate.now().plusDays(30));
        movements.add(movement1);

        StockMovementItemDTO movement2 = new StockMovementItemDTO();
        movement2.setProductId(product2.getId());
        movement2.setQuantityDelta(new BigDecimal("-3.0"));
        movement2.setMovementType(MovementType.MODIFICACION);
        movement2.setExpirationDate(java.time.LocalDate.now().plusDays(30));
        movements.add(movement2);

        batchRequest.setMovements(movements);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(asJsonString(batchRequest))
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.processedCount", is(2)));

        Product updatedProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("105.000").compareTo(updatedProduct1.getCurrentStock()));

        Product updatedProduct2 = productRepository.findById(product2.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("47.000").compareTo(updatedProduct2.getCurrentStock()));
    }
}
