package com.economato.inventory.infrastructure.adapter.in.web.product;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;

import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.domain.model.product.Supplier;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                                "\"unit\":\"KG\"," +
                                "\"supplierId\":" + supplierId + "," +
                                "\"price\":3.15," +
                                "\"stock\":100," +
                                "\"expirationDate\":\"2030-01-01\"" +
                                "}";

                mockMvc.perform(post("/api/products")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonPayload))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("Abdejo Filete Cong Piel"))
                                .andExpect(jsonPath("$.unitPrice").value(3.15))
                                .andExpect(jsonPath("$.currentStock").value(100));
        }
}
