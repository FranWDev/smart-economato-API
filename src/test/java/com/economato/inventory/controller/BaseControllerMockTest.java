package com.economato.inventory.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.economato.inventory.service.InventoryAuditService;
import com.economato.inventory.service.OrderAuditService;
import com.economato.inventory.service.RecipeAuditService;
import com.economato.inventory.service.StockLedgerService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseControllerMockTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected StockLedgerService stockLedgerService;

    @MockBean
    protected InventoryAuditService inventoryAuditService;

    @MockBean
    protected OrderAuditService orderAuditService;

    @MockBean
    protected RecipeAuditService recipeAuditService;
}
