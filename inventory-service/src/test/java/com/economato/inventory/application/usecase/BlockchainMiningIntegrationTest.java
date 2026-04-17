package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.LedgerBlockRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración para validar que el proceso de minado de la blockchain funciona correctamente
 * de forma asíncrona y sin errores transaccionales.
 */
@ActiveProfiles({"test", "kafka-test"})
public class BlockchainMiningIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private StockLedgerRepository ledgerRepository;

    @Autowired
    private LedgerBlockRepository blockRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void whenSufficientTransactionsAreRecorded_thenBlockIsMinedAsynchronously() throws Exception {
        // 1. Preparación - Crear un producto para las transacciones
        clearDatabase();
        
        Product product = new Product();
        product.setName("Blockchain Test Product");
        product.setUnit("UD");
        product.setUnitPrice(BigDecimal.TEN);
        product.setProductCode("BC-TEST-001");
        product.setCurrentStock(BigDecimal.ZERO);
        product = productRepository.saveAndFlush(product);

        // Asegurarnos de que el bloque génesis existe (el servicio lo crea al arrancar o al minar)
        long initialBlocks = blockRepository.count();
        if (initialBlocks == 0) {
            // Si por alguna razón no existe el genesis, el primer minado lo creará
            // pero para este test esperamos que el sistema esté inicializado.
        }

        // 2. Acción - Registrar 10 transacciones (blockSize por defecto es 10)
        // Cada registro dispara un NewLedgerTransactionEvent que el BlockchainService escucha de forma asíncrona.
        for (int i = 0; i < 10; i++) {
            stockLedgerService.recordStockMovement(
                    product.getId(),
                    BigDecimal.ONE,
                    MovementType.ENTRADA,
                    "TX Test Blockchain " + i,
                    null,
                    null,
                    java.time.LocalDate.now().plusMonths(1)
            );
        }

        // 3. Verificación - Esperar a que el proceso asíncrono termine
        // Usamos un bucle de reintento simple para no depender de Awaitility si no está en el classpath
        boolean mined = false;
        for (int i = 0; i < 20; i++) { // Reintentar durante 10 segundos
            if (blockRepository.count() > initialBlocks) {
                mined = true;
                break;
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }

        // 4. Aserciones
        assertTrue(mined, "Se debería haber minado al menos un bloque nuevo tras alcanzar el blockSize de 10");
        
        long finalBlocks = blockRepository.count();
        assertTrue(finalBlocks >= 1, "Debe existir al menos el bloque génesis y un bloque minado");
        
        // Verificar que las transacciones pendientes se han reducido (asignadas al bloque)
        long pending = ledgerRepository.countByBlockIsNull();
        assertTrue(pending < 10, "Las transacciones deberían haber sido asignadas al bloque (pendientes: " + pending + ")");
    }
}
