package com.economato.inventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.dto.request.OrderRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

class OrderControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/orders";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private String jwtToken;
        private User testUser;
        private Product testProduct1;
        private Product testProduct2;

        @BeforeEach
        public void setUp() throws Exception {
                clearDatabase();

                testUser = TestDataUtil.createAdminUser();
                testUser.setPassword(passwordEncoder.encode("admin123"));
                testUser = userRepository.saveAndFlush(testUser);

                testProduct1 = TestDataUtil.createFlour();
                testProduct1 = productRepository.saveAndFlush(testProduct1);

                testProduct2 = TestDataUtil.createSugar();
                testProduct2 = productRepository.saveAndFlush(testProduct2);

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

        @Test
        void whenGetAllOrders_thenReturnsPaginatedOrders() throws Exception {
                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk());
        }

        @Test
        void whenCreateValidOrder_thenReturnsCreatedOrder() throws Exception {

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(testUser.getId());

                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
                detail.setProductId(testProduct1.getId());
                detail.setQuantity(new BigDecimal("2.5"));
                details.add(detail);
                orderRequest.setDetails(details);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.userId", is(testUser.getId())))
                                .andExpect(jsonPath("$.details", hasSize(1)))
                                .andExpect(jsonPath("$.details[0].productId", is(testProduct1.getId())))
                                .andExpect(jsonPath("$.details[0].quantity", is(2.5)));
        }

        @Test
        void whenGetOrderById_thenReturnsOrder() throws Exception {

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(testUser.getId());

                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
                detail.setProductId(testProduct1.getId());
                detail.setQuantity(new BigDecimal("2.5"));
                details.add(detail);
                orderRequest.setDetails(details);

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer orderId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/{id}", orderId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.userId", is(testUser.getId())))
                                .andExpect(jsonPath("$.details[0].productId", is(testProduct1.getId())))
                                .andExpect(jsonPath("$.details[0].quantity", is(2.5)));
        }

        @Test
        void whenUpdateOrder_thenReturnsUpdatedOrder() throws Exception {

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(testUser.getId());

                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
                detail.setProductId(testProduct1.getId());
                detail.setQuantity(new BigDecimal("2.5"));
                details.add(detail);
                orderRequest.setDetails(details);

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer orderId = objectMapper.readTree(response).get("id").asInt();

                List<OrderDetailRequestDTO> updatedDetails = new ArrayList<>();
                OrderDetailRequestDTO updatedDetail = new OrderDetailRequestDTO();
                updatedDetail.setProductId(testProduct2.getId());
                updatedDetail.setQuantity(new BigDecimal("3.0"));
                updatedDetails.add(updatedDetail);
                orderRequest.setDetails(updatedDetails);

                mockMvc.perform(put(BASE_URL + "/{id}", orderId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.userId", is(testUser.getId())))
                                .andExpect(jsonPath("$.details[0].productId", is(testProduct2.getId())))
                                .andExpect(jsonPath("$.details[0].quantity", is(3.0)));
        }

        @Test
        void whenDeleteOrder_thenReturnsNoContent() throws Exception {

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(testUser.getId());

                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
                detail.setProductId(testProduct1.getId());
                detail.setQuantity(new BigDecimal("2.5"));
                details.add(detail);
                orderRequest.setDetails(details);

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer orderId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(delete(BASE_URL + "/{id}", orderId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", orderId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenGetOrdersByUser_thenReturnsUserOrders() throws Exception {

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(testUser.getId());
                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
                detail.setProductId(testProduct1.getId());
                detail.setQuantity(new BigDecimal("2.5"));
                details.add(detail);
                orderRequest.setDetails(details);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk());

                mockMvc.perform(get(BASE_URL + "/user/{userId}", testUser.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }

        @Test
        void whenGetOrdersByStatus_thenReturnsOrdersWithStatus() throws Exception {
                mockMvc.perform(get(BASE_URL + "/status/{status}", "CREATED")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }

        @Test
        void whenGetOrdersByDateRange_thenReturnsOrders() throws Exception {
                mockMvc.perform(get(BASE_URL + "/daterange")
                                .param("start", "2026-01-01T00:00:00")
                                .param("end", "2026-12-31T23:59:59")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }

        @Test
        void whenGetPendingReceptionOrders_thenReturnsPendingOrders() throws Exception {
                mockMvc.perform(get(BASE_URL + "/reception/pending")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }

        @Test
        void whenRegisterOrderReception_thenUpdatesOrder() throws Exception {

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(testUser.getId());
                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
                detail.setProductId(testProduct1.getId());
                detail.setQuantity(new BigDecimal("5.0"));
                details.add(detail);
                orderRequest.setDetails(details);

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CREATED"))
                                .andExpect(jsonPath("$.details").isArray())
                                .andExpect(jsonPath("$.details[0].productId").value(testProduct1.getId()))
                                .andReturn().getResponse().getContentAsString();

                Integer orderId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/" + orderId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(orderId))
                                .andExpect(jsonPath("$.details[0].productId").value(testProduct1.getId()));

                var receptionData = new java.util.HashMap<String, Object>();
                receptionData.put("orderId", orderId);
                receptionData.put("status", "IN_REVIEW");
                var items = new java.util.ArrayList<java.util.Map<String, Object>>();
                var item = new java.util.HashMap<String, Object>();
                item.put("productId", testProduct1.getId());
                item.put("quantityReceived", 5.0);
                items.add(item);
                receptionData.put("items", items);

                mockMvc.perform(post(BASE_URL + "/reception")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(receptionData)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                                .andExpect(jsonPath("$.id").value(orderId));
        }

        @Test
        void whenDownloadOrderPdf_WithValidOrder_thenReturnsPdf() throws Exception {

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(testUser.getId());

                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
                detail.setProductId(testProduct1.getId());
                detail.setQuantity(new BigDecimal("2.0"));
                details.add(detail);
                orderRequest.setDetails(details);

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer orderId = objectMapper.readTree(response).get("id").asInt();

                byte[] pdfBytes = mockMvc.perform(get(BASE_URL + "/{id}/pdf", orderId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Type", "application/pdf"))
                                .andExpect(header().string("Content-Disposition",
                                                "attachment; filename=\"pedido_" + orderId + ".pdf\""))
                                .andReturn().getResponse().getContentAsByteArray();

                assertNotNull(pdfBytes);
                assertTrue(pdfBytes.length > 0);

                String pdfHeader = new String(java.util.Arrays.copyOfRange(pdfBytes, 0, 4));
                assertTrue(pdfHeader.startsWith("%PDF"));
        }

        @Test
        void whenDownloadOrderPdf_WithMultipleDetails_thenReturnsPdf() throws Exception {

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(testUser.getId());

                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail1 = new OrderDetailRequestDTO();
                detail1.setProductId(testProduct1.getId());
                detail1.setQuantity(new BigDecimal("1.5"));
                details.add(detail1);

                OrderDetailRequestDTO detail2 = new OrderDetailRequestDTO();
                detail2.setProductId(testProduct2.getId());
                detail2.setQuantity(new BigDecimal("2.0"));
                details.add(detail2);

                orderRequest.setDetails(details);

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer orderId = objectMapper.readTree(response).get("id").asInt();

                byte[] pdfBytes = mockMvc.perform(get(BASE_URL + "/{id}/pdf", orderId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Type", "application/pdf"))
                                .andExpect(header().string("Content-Disposition",
                                                "attachment; filename=\"pedido_" + orderId + ".pdf\""))
                                .andReturn().getResponse().getContentAsByteArray();

                assertNotNull(pdfBytes);
                assertTrue(pdfBytes.length > 0);
        }

        @Test
        void whenDownloadOrderPdf_WithLongUserName_thenReturnsPdf() throws Exception {

                User longNameUser = TestDataUtil.createAdminUser();
                longNameUser.setName("Usuario con un nombre extremadamente largo para el PDF de orden");
                longNameUser.setUser("adminUser" + System.currentTimeMillis());
                longNameUser.setPassword(passwordEncoder.encode("admin123"));
                longNameUser = userRepository.saveAndFlush(longNameUser);

                OrderRequestDTO orderRequest = new OrderRequestDTO();
                orderRequest.setUserId(longNameUser.getId());

                List<OrderDetailRequestDTO> details = new ArrayList<>();
                OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
                detail.setProductId(testProduct1.getId());
                detail.setQuantity(new BigDecimal("1.0"));
                details.add(detail);
                orderRequest.setDetails(details);

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(orderRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer orderId = objectMapper.readTree(response).get("id").asInt();

                byte[] pdfBytes = mockMvc.perform(get(BASE_URL + "/{id}/pdf", orderId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Type", "application/pdf"))
                                .andExpect(header().string("Content-Disposition",
                                                "attachment; filename=\"pedido_" + orderId + ".pdf\""))
                                .andReturn().getResponse().getContentAsByteArray();

                assertNotNull(pdfBytes);
                assertTrue(pdfBytes.length > 0);
        }

        @Test
        void whenDownloadOrderPdf_WithNonExistentOrder_thenReturnsNotFound() throws Exception {
                mockMvc.perform(get(BASE_URL + "/{id}/pdf", 99999)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }
}