package com.economato.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.ProductResponseDTO;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.StockLedgerRepository;
import com.economato.inventory.repository.StockSnapshotRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.service.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ProductService - Stock Ledger Integration Tests")
class ProductServiceStockLedgerIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockLedgerRepository stockLedgerRepository;

    @Autowired
    private StockSnapshotRepository snapshotRepository;

    @Autowired
    private UserRepository userRepository;

    private Product testProduct;
    private User testUser;

    @BeforeEach
    void setUp() {

        stockLedgerRepository.deleteAll();
        snapshotRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setName("test_user");
        testUser.setPassword("hashed_password");
        testUser.setRole(Role.ADMIN);
        testUser.setEmail("test@example.com");
        testUser = userRepository.saveAndFlush(testUser);

        testProduct = new Product();
        testProduct.setName("Test Product - Ledger");
        testProduct.setType("Ingredient");
        testProduct.setUnit("KG");
        testProduct.setUnitPrice(new BigDecimal("10.50"));
        testProduct.setProductCode("TEST-LEDGER-001");
        testProduct.setCurrentStock(new BigDecimal("100.0"));
        testProduct = productRepository.saveAndFlush(testProduct);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                testUser.getName(), null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Debe registrar en StockLedger cuando se aumenta el stock")
    void testUpdateStockManually_IncreasesStock_RecordsInLedger() {

        BigDecimal initialStock = testProduct.getCurrentStock();
        BigDecimal newStock = initialStock.add(new BigDecimal("50.0"));

        ProductRequestDTO requestDTO = new ProductRequestDTO();
        requestDTO.setName(testProduct.getName());
        requestDTO.setType(testProduct.getType());
        requestDTO.setUnit(testProduct.getUnit());
        requestDTO.setUnitPrice(testProduct.getUnitPrice());
        requestDTO.setProductCode(testProduct.getProductCode());
        requestDTO.setCurrentStock(newStock);

        Optional<ProductResponseDTO> result = productService.updateStockManually(
                testProduct.getId(), requestDTO);

        assertTrue(result.isPresent());
        assertEquals(0, newStock.compareTo(result.get().getCurrentStock()));

        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(0, newStock.compareTo(updatedProduct.getCurrentStock()));

        List<StockLedger> ledgerEntries = stockLedgerRepository
                .findByProductIdOrderBySequenceNumber(testProduct.getId());

        assertFalse(ledgerEntries.isEmpty(), "Debe haber al menos una entrada en el ledger");

        StockLedger latestEntry = ledgerEntries.get(ledgerEntries.size() - 1);
        assertEquals(0, new BigDecimal("50.0").compareTo(latestEntry.getQuantityDelta()));
        assertEquals(0, newStock.compareTo(latestEntry.getResultingStock()));
        assertTrue(latestEntry.getDescription().contains("Modificación manual"));
        assertNull(latestEntry.getOrderId(), "orderId debe ser null para modificación manual");
    }

    @Test
    @DisplayName("Debe registrar en StockLedger cuando se disminuye el stock")
    void testUpdateStockManually_DecreasesStock_RecordsInLedger() {

        BigDecimal initialStock = testProduct.getCurrentStock();
        BigDecimal newStock = initialStock.subtract(new BigDecimal("30.0"));

        ProductRequestDTO requestDTO = new ProductRequestDTO();
        requestDTO.setName(testProduct.getName());
        requestDTO.setType(testProduct.getType());
        requestDTO.setUnit(testProduct.getUnit());
        requestDTO.setUnitPrice(testProduct.getUnitPrice());
        requestDTO.setProductCode(testProduct.getProductCode());
        requestDTO.setCurrentStock(newStock);

        Optional<ProductResponseDTO> result = productService.updateStockManually(
                testProduct.getId(), requestDTO);

        assertTrue(result.isPresent());
        assertEquals(0, newStock.compareTo(result.get().getCurrentStock()));

        List<StockLedger> ledgerEntries = stockLedgerRepository
                .findByProductIdOrderBySequenceNumber(testProduct.getId());

        assertFalse(ledgerEntries.isEmpty());

        StockLedger latestEntry = ledgerEntries.get(ledgerEntries.size() - 1);
        assertEquals(0, new BigDecimal("-30.0").compareTo(latestEntry.getQuantityDelta()));
        assertEquals(0, newStock.compareTo(latestEntry.getResultingStock()));
    }

    @Test
    @DisplayName("NO debe registrar en StockLedger cuando el stock no cambia")
    void testUpdateStockManually_NoStockChange_DoesNotRegister() {

        BigDecimal currentStock = testProduct.getCurrentStock();

        ProductRequestDTO requestDTO = new ProductRequestDTO();
        requestDTO.setName(testProduct.getName() + " Modificado");
        requestDTO.setType(testProduct.getType());
        requestDTO.setUnit(testProduct.getUnit());
        requestDTO.setUnitPrice(testProduct.getUnitPrice().add(new BigDecimal("1.0")));
        requestDTO.setProductCode(testProduct.getProductCode());
        requestDTO.setCurrentStock(currentStock);

        int initialCount = stockLedgerRepository
                .findByProductIdOrderBySequenceNumber(testProduct.getId()).size();
        assertEquals(0, initialCount);

        Optional<ProductResponseDTO> result = productService.updateStockManually(
                testProduct.getId(), requestDTO);

        assertTrue(result.isPresent());
        assertEquals(currentStock, result.get().getCurrentStock());
        assertEquals("Test Product - Ledger Modificado", result.get().getName());

        List<StockLedger> ledgerEntries = stockLedgerRepository
                .findByProductIdOrderBySequenceNumber(testProduct.getId());

        assertTrue(ledgerEntries.isEmpty(),
                "No debe haber entradas en el ledger si el stock no cambia");
    }

    @Test
    @DisplayName("Debe mantener la cadena criptográfica con múltiples actualizaciones")
    void testUpdateStockManually_MultipleUpdates_MaintainsCryptoChain() {

        ProductRequestDTO requestDTO1 = new ProductRequestDTO();
        requestDTO1.setName(testProduct.getName());
        requestDTO1.setType(testProduct.getType());
        requestDTO1.setUnit(testProduct.getUnit());
        requestDTO1.setUnitPrice(testProduct.getUnitPrice());
        requestDTO1.setProductCode(testProduct.getProductCode());
        requestDTO1.setCurrentStock(testProduct.getCurrentStock().add(new BigDecimal("25.0")));

        productService.updateStockManually(testProduct.getId(), requestDTO1);

        ProductRequestDTO requestDTO2 = new ProductRequestDTO();
        requestDTO2.setName(testProduct.getName());
        requestDTO2.setType(testProduct.getType());
        requestDTO2.setUnit(testProduct.getUnit());
        requestDTO2.setUnitPrice(testProduct.getUnitPrice());
        requestDTO2.setProductCode(testProduct.getProductCode());
        requestDTO2.setCurrentStock(
                testProduct.getCurrentStock().add(new BigDecimal("25.0")).add(new BigDecimal("35.0")));

        productService.updateStockManually(testProduct.getId(), requestDTO2);

        List<StockLedger> ledgerEntries = stockLedgerRepository
                .findByProductIdOrderBySequenceNumber(testProduct.getId());

        assertEquals(2, ledgerEntries.size(), "Debe haber 2 entradas en el ledger");
        assertEquals(1L, ledgerEntries.get(0).getSequenceNumber());
        assertEquals(2L, ledgerEntries.get(1).getSequenceNumber());

        String firstHash = ledgerEntries.get(0).getCurrentHash();
        String secondPreviousHash = ledgerEntries.get(1).getPreviousHash();
        assertEquals(firstHash, secondPreviousHash,
                "La cadena criptográfica debe estar intacta");
    }

    @Test
    @DisplayName("Debe registrar usuario autenticado en el ledger")
    void testUpdateStockManually_RecordsAuthenticatedUser() {

        BigDecimal newStock = testProduct.getCurrentStock().add(new BigDecimal("20.0"));

        ProductRequestDTO requestDTO = new ProductRequestDTO();
        requestDTO.setName(testProduct.getName());
        requestDTO.setType(testProduct.getType());
        requestDTO.setUnit(testProduct.getUnit());
        requestDTO.setUnitPrice(testProduct.getUnitPrice());
        requestDTO.setProductCode(testProduct.getProductCode());
        requestDTO.setCurrentStock(newStock);

        productService.updateStockManually(testProduct.getId(), requestDTO);

        List<StockLedger> ledgerEntries = stockLedgerRepository
                .findByProductIdOrderBySequenceNumber(testProduct.getId());

        assertFalse(ledgerEntries.isEmpty());

        StockLedger entry = ledgerEntries.get(0);
        assertNotNull(entry.getUser(), "El usuario debe estar registrado en el ledger");
        assertEquals(testUser.getId(), entry.getUser().getId());
    }
}
