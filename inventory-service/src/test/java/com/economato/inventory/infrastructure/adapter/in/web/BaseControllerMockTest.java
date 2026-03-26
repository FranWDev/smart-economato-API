package com.economato.inventory.infrastructure.adapter.in.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.economato.inventory.application.usecase.InventoryAuditService;
import com.economato.inventory.application.usecase.OrderAuditService;
import com.economato.inventory.application.usecase.RecipeAuditService;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseControllerMockTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected AuditEventProducer auditEventProducer;

    @MockitoBean
    protected StockLedgerService stockLedgerService;

    @MockitoBean
    protected InventoryAuditService inventoryAuditService;

    @MockitoBean
    protected OrderAuditService orderAuditService;

    @MockitoBean
    protected RecipeAuditService recipeAuditService;
}
