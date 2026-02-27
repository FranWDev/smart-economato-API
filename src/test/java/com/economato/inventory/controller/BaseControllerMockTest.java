package com.economato.inventory.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    protected StockLedgerService stockLedgerService;

    @MockitoBean
    protected InventoryAuditService inventoryAuditService;

    @MockitoBean
    protected OrderAuditService orderAuditService;

    @MockitoBean
    protected RecipeAuditService recipeAuditService;
}
