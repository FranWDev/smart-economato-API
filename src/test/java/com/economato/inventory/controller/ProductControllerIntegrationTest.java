package com.economato.inventory.controller;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

class ProductControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/products";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private Product testProduct;
        private String jwtToken;

        @BeforeEach
        void setUp() throws Exception {
                clearDatabase();

                User adminUser = TestDataUtil.createAdminUser();
                adminUser.setPassword(passwordEncoder.encode("admin123"));
                userRepository.saveAndFlush(adminUser);

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(adminUser.getName());
                loginRequest.setPassword("admin123");

                String response = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                jwtToken = loginResponse.getToken();

                testProduct = TestDataUtil.createFlour();
                testProduct = productRepository.saveAndFlush(testProduct);
        }

        @Test
        void whenGetAllProducts_thenReturnsPaginatedProducts() throws Exception {
                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", notNullValue()));
        }

        @Test
        void whenCreateValidProduct_thenReturnsCreatedProduct() throws Exception {
                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.type").value(productRequest.getType()))
                                .andExpect(jsonPath("$.unit").value(productRequest.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(productRequest.getProductCode()))
                                .andExpect(jsonPath("$.currentStock")
                                                .value(productRequest.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock")
                                                .value(productRequest.getMinimumStock().doubleValue()));
        }

        @Test
        void whenGetProductById_thenReturnsProduct() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.type").value(productRequest.getType()))
                                .andExpect(jsonPath("$.unit").value(productRequest.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(productRequest.getProductCode()))
                                .andExpect(jsonPath("$.currentStock")
                                                .value(productRequest.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock")
                                                .value(productRequest.getMinimumStock().doubleValue()))
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.type").value(productRequest.getType()))
                                .andExpect(jsonPath("$.unit").value(productRequest.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(productRequest.getProductCode()))
                                .andExpect(jsonPath("$.currentStock")
                                                .value(productRequest.getCurrentStock().doubleValue()));
        }

        @Test
        void whenSearchProductsByName_thenReturnsMatchingProducts() throws Exception {

                ProductRequestDTO product1 = TestDataUtil.createProductRequestDTO();
                product1.setName("Leche Desnatada");
                product1.setProductCode("CODE001");

                ProductRequestDTO product2 = TestDataUtil.createProductRequestDTO();
                product2.setName("Leche Entera");
                product2.setProductCode("CODE002");

                ProductRequestDTO product3 = TestDataUtil.createProductRequestDTO();
                product3.setName("Yogur Natural");
                product3.setProductCode("CODE003");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(product1)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(product1.getName()))
                                .andExpect(jsonPath("$.type").value(product1.getType()))
                                .andExpect(jsonPath("$.unit").value(product1.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(product1.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(product1.getProductCode()))
                                .andExpect(jsonPath("$.currentStock").value(product1.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock").value(product1.getMinimumStock().doubleValue()));

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(product2)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(product2.getName()))
                                .andExpect(jsonPath("$.type").value(product2.getType()))
                                .andExpect(jsonPath("$.unit").value(product2.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(product2.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(product2.getProductCode()))
                                .andExpect(jsonPath("$.currentStock").value(product2.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock").value(product2.getMinimumStock().doubleValue()));

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(product3)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(product3.getName()))
                                .andExpect(jsonPath("$.type").value(product3.getType()))
                                .andExpect(jsonPath("$.unit").value(product3.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(product3.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(product3.getProductCode()))
                                .andExpect(jsonPath("$.currentStock").value(product3.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock").value(product3.getMinimumStock().doubleValue()));

                mockMvc.perform(get(BASE_URL + "/search")
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("name", "leche")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", notNullValue()))
                                .andExpect(jsonPath("$.content.length()").value(2))
                                .andExpect(jsonPath("$.content[0].name", containsString("Leche")))
                                .andExpect(jsonPath("$.content[1].name", containsString("Leche")));
        }

        @Test
        void whenSearchProductsByNameWithNoMatches_thenReturnsEmptyList() throws Exception {
                mockMvc.perform(get(BASE_URL + "/search")
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("name", "ProductoInexistente")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", notNullValue()))
                                .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        void whenDeleteProduct_thenReturnsNoContent() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.type").value(productRequest.getType()))
                                .andExpect(jsonPath("$.unit").value(productRequest.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(productRequest.getProductCode()))
                                .andExpect(jsonPath("$.currentStock")
                                                .value(productRequest.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock")
                                                .value(productRequest.getMinimumStock().doubleValue()))
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(delete(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenGetProductByCodebar_thenReturnsProduct() throws Exception {
                mockMvc.perform(get(BASE_URL + "/codebar/{codebar}", testProduct.getProductCode())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productCode").value(testProduct.getProductCode()))
                                .andExpect(jsonPath("$.name").value(testProduct.getName()));
        }

        @Test
        void whenGetProductByInvalidCodebar_thenReturnsNotFound() throws Exception {
                mockMvc.perform(get(BASE_URL + "/codebar/{codebar}", "INVALID-CODE")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenUpdateStockManually_WithStockChange_thenRegistersInStockLedger() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();
                productRequest.setCurrentStock(new BigDecimal("100.0"));

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.type").value(productRequest.getType()))
                                .andExpect(jsonPath("$.unit").value(productRequest.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(productRequest.getProductCode()))
                                .andExpect(jsonPath("$.currentStock")
                                                .value(productRequest.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock")
                                                .value(productRequest.getMinimumStock().doubleValue()))
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                productRequest.setCurrentStock(new BigDecimal("150.0"));

                mockMvc.perform(put(BASE_URL + "/{id}/stock-manual", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentStock").value(150.0));

                mockMvc.perform(get(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentStock").value(150.0));
        }

        @Test
        void whenUpdateStockManually_WithoutStockChange_thenDoesNotRegister() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();
                productRequest.setCurrentStock(new BigDecimal("100.0"));

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.type").value(productRequest.getType()))
                                .andExpect(jsonPath("$.unit").value(productRequest.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(productRequest.getProductCode()))
                                .andExpect(jsonPath("$.currentStock")
                                                .value(productRequest.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock")
                                                .value(productRequest.getMinimumStock().doubleValue()))
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                productRequest.setName(productRequest.getName() + " Actualizado");
                productRequest.setUnitPrice(productRequest.getUnitPrice().add(new BigDecimal("0.50")));

                mockMvc.perform(put(BASE_URL + "/{id}/stock-manual", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.currentStock").value(100.0));
        }

        @Test
        void whenUpdateStockManually_DecreasesStock_thenRegistersInStockLedger() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();
                productRequest.setCurrentStock(new BigDecimal("200.0"));

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.type").value(productRequest.getType()))
                                .andExpect(jsonPath("$.unit").value(productRequest.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(productRequest.getProductCode()))
                                .andExpect(jsonPath("$.currentStock")
                                                .value(productRequest.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock")
                                                .value(productRequest.getMinimumStock().doubleValue()))
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                productRequest.setCurrentStock(new BigDecimal("150.0"));

                mockMvc.perform(put(BASE_URL + "/{id}/stock-manual", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentStock").value(150.0));

                mockMvc.perform(get(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentStock").value(150.0));
        }

        @Test
        void whenUpdateStockManually_WithInvalidData_thenReturnsBadRequest() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value(productRequest.getName()))
                                .andExpect(jsonPath("$.type").value(productRequest.getType()))
                                .andExpect(jsonPath("$.unit").value(productRequest.getUnit()))
                                .andExpect(jsonPath("$.unitPrice").value(productRequest.getUnitPrice().doubleValue()))
                                .andExpect(jsonPath("$.productCode").value(productRequest.getProductCode()))
                                .andExpect(jsonPath("$.currentStock")
                                                .value(productRequest.getCurrentStock().doubleValue()))
                                .andExpect(jsonPath("$.minimumStock")
                                                .value(productRequest.getMinimumStock().doubleValue()))
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                ProductRequestDTO invalidRequest = new ProductRequestDTO();
                invalidRequest.setName("");
                invalidRequest.setType(productRequest.getType());
                invalidRequest.setUnit(productRequest.getUnit());
                invalidRequest.setUnitPrice(productRequest.getUnitPrice());
                invalidRequest.setProductCode(productRequest.getProductCode());
                invalidRequest.setCurrentStock(productRequest.getCurrentStock());

                mockMvc.perform(put(BASE_URL + "/{id}/stock-manual", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(invalidRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void whenDownloadProductsExcel_thenReturnsExcelFile() throws Exception {
                // StreamingResponseBody es asíncrono: hay que esperar a que el async
                // inicie (asyncStarted) antes de hacer el dispatch final.
                org.springframework.test.web.servlet.MvcResult mvcResult = mockMvc
                                .perform(get(BASE_URL + "/export/excel")
                                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(request().asyncStarted())
                                .andReturn();

                byte[] excelBytes = mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Type",
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .andExpect(header().string("Content-Disposition",
                                                "attachment; filename=\"productos.xlsx\""))
                                .andReturn().getResponse().getContentAsByteArray();

                assertNotNull(excelBytes);
                assertTrue(excelBytes.length > 0);
        }

        @Test
        void whenCreateProductWithAliases_thenSuccess() throws Exception {
                String jsonPayload = "{" +
                                "\"name\":\"Abdejo Aliased\"," +
                                "\"productCode\":\"ALIAS123\"," +
                                "\"type\":\"Ingrediente\"," +
                                "\"unit\":\"KG\"," +
                                "\"price\":5.50," +
                                "\"stock\":100," +
                                "\"minStock\":10" +
                                "}";

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonPayload))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("Abdejo Aliased"))
                                .andExpect(jsonPath("$.unitPrice").value(5.5))
                                .andExpect(jsonPath("$.currentStock").value(100.0))
                                .andExpect(jsonPath("$.minimumStock").value(10.0));
        }
}
