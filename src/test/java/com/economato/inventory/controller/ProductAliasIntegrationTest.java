package com.economato.inventory.controller;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.Supplier;
import com.economato.inventory.repository.SupplierRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProductAliasIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private SupplierRepository supplierRepository;

        @Autowired
        private UserRepository userRepository;

        private String token;
        private Integer supplierId;

        @BeforeEach
        void setUp() throws Exception {
                clearDatabase();

                // Create admin user and get token
                userRepository.save(TestDataUtil.createAdminUser());
                LoginRequestDTO loginRequest = new LoginRequestDTO("adminUser", "admin123");
                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                LoginResponseDTO loginResponse = objectMapper.readValue(
                                loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);
                token = loginResponse.getToken();

                // Create a supplier
                Supplier supplier = new Supplier();
                supplier.setName("Test Supplier");
                supplierId = supplierRepository.save(supplier).getId();
        }

        @Test
        void whenCreateProductWithAliases_thenSuccess() throws Exception {
                // Payload using aliases: minStock, price, stock
                String jsonPayload = "{" +
                                "\"name\":\"Abdejo Filete Cong Piel\"," +
                                "\"productCode\":\"166582816277\"," +
                                "\"type\":\"\"," +
                                "\"unit\":\"KG\"," +
                                "\"supplierId\":" + supplierId + "," +
                                "\"price\":3.15," +
                                "\"stock\":100," +
                                "\"minStock\":10" +
                                "}";

                mockMvc.perform(post("/api/products")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonPayload))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("Abdejo Filete Cong Piel"))
                                .andExpect(jsonPath("$.unitPrice").value(3.15))
                                .andExpect(jsonPath("$.currentStock").value(100))
                                .andExpect(jsonPath("$.minimumStock").value(10));
        }
}
