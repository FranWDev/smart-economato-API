package com.economato.inventory.infrastructure.adapter.in.web.order;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;

import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.order.request.OrderDetailRequestDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.domain.model.order.Order;
import com.economato.inventory.domain.model.order.OrderStatus;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.domain.model.product.Supplier;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.shared.TestDataUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

public class OrderDetailControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/order-details";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private SupplierRepository supplierRepository;

        private String jwtToken;
        private User testUser;
        private Product testProduct;
        private Order testOrder;

        @BeforeEach
        void setUp() throws Exception {
                clearDatabase();

                testUser = TestDataUtil.createAdminUser();
                userRepository.saveAndFlush(testUser);

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

                testProduct = new Product();
                testProduct.setName("Producto test");
                testProduct.setProductCode("TEST-001");
                testProduct.setUnit("unidad");
                testProduct.setUnitPrice(new BigDecimal("10.00"));
                testProduct.setCurrentStock(new BigDecimal("100"));
 // Required field
                productRepository.save(testProduct);

                Supplier supplier = Supplier.builder()
                        .name("Test Supplier ODCIT")
                        .email("odcit@test.com")
                        .phone("111222333")
                        .build();
                supplier = supplierRepository.saveAndFlush(supplier);

                testOrder = new Order();
                testOrder.setUser(testUser);
                testOrder.setSupplier(supplier);
                testOrder.setOrderDate(LocalDateTime.now());
                testOrder.setStatus(OrderStatus.PENDING);
                orderRepository.save(testOrder);
        }

        @Test
        public void whenCreateOrderDetail_thenSuccess() throws Exception {
                OrderDetailRequestDTO detailRequest = new OrderDetailRequestDTO();
                detailRequest.setOrderId(testOrder.getId());
                detailRequest.setProductId(testProduct.getId());
                detailRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(detailRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.productId").value(detailRequest.getProductId()))
                                .andExpect(jsonPath("$.quantity").value(detailRequest.getQuantity().doubleValue()))
                                .andExpect(jsonPath("$.productName").exists())
                                .andExpect(jsonPath("$.unitPrice").exists())
                                .andExpect(jsonPath("$.subtotal").exists());
        }

        @Test
        public void whenGetAllOrderDetails_thenSuccess() throws Exception {

                OrderDetailRequestDTO detailRequest = new OrderDetailRequestDTO();
                detailRequest.setOrderId(testOrder.getId());
                detailRequest.setProductId(testProduct.getId());
                detailRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(detailRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                                .andExpect(jsonPath("$.content[0].productId").exists())
                                .andExpect(jsonPath("$.content[0].productName").exists())
                                .andExpect(jsonPath("$.content[0].quantity").exists())
                                .andExpect(jsonPath("$.content[0].unitPrice").exists())
                                .andExpect(jsonPath("$.content[0].subtotal").exists());
        }

        @Test
        public void whenGetDetailsByOrderId_thenSuccess() throws Exception {

                OrderDetailRequestDTO detailRequest = new OrderDetailRequestDTO();
                detailRequest.setOrderId(testOrder.getId());
                detailRequest.setProductId(testProduct.getId());
                detailRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(detailRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/order/{orderId}", testOrder.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                                .andExpect(jsonPath("$[0].productId").exists())
                                .andExpect(jsonPath("$[0].productName").exists())
                                .andExpect(jsonPath("$[0].quantity").exists())
                                .andExpect(jsonPath("$[0].unitPrice").exists())
                                .andExpect(jsonPath("$[0].subtotal").exists());
        }

        @Test
        public void whenUpdateOrderDetail_thenSuccess() throws Exception {

                OrderDetailRequestDTO detailRequest = new OrderDetailRequestDTO();
                detailRequest.setOrderId(testOrder.getId());
                detailRequest.setProductId(testProduct.getId());
                detailRequest.setQuantity(new BigDecimal("2.5"));

                @SuppressWarnings("unused")
                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(detailRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                detailRequest.setQuantity(new BigDecimal("3.5"));

                mockMvc.perform(put(BASE_URL + "/{orderId}/{productId}",
                                testOrder.getId(), testProduct.getId())
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(detailRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productId").value(detailRequest.getProductId()))
                                .andExpect(jsonPath("$.quantity").value(detailRequest.getQuantity().doubleValue()))
                                .andExpect(jsonPath("$.productName").exists())
                                .andExpect(jsonPath("$.unitPrice").exists())
                                .andExpect(jsonPath("$.subtotal").exists());
        }

        @Test
        public void whenDeleteOrderDetail_thenSuccess() throws Exception {

                OrderDetailRequestDTO detailRequest = new OrderDetailRequestDTO();
                detailRequest.setOrderId(testOrder.getId());
                detailRequest.setProductId(testProduct.getId());
                detailRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(detailRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(delete(BASE_URL + "/{orderId}/{productId}",
                                testOrder.getId(), testProduct.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/order/{orderId}", testOrder.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        public void whenGetOrderDetailById_thenSuccess() throws Exception {
                OrderDetailRequestDTO detailRequest = new OrderDetailRequestDTO();
                detailRequest.setOrderId(testOrder.getId());
                detailRequest.setProductId(testProduct.getId());
                detailRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(detailRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/{orderId}/{productId}",
                                testOrder.getId(), testProduct.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productId").value(testProduct.getId()))
                                .andExpect(jsonPath("$.quantity").value(2.5));
        }

        @Test
        public void whenGetOrderDetailsByProduct_thenSuccess() throws Exception {
                OrderDetailRequestDTO detailRequest = new OrderDetailRequestDTO();
                detailRequest.setOrderId(testOrder.getId());
                detailRequest.setProductId(testProduct.getId());
                detailRequest.setQuantity(new BigDecimal("2.5"));

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(detailRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL + "/product/{productId}", testProduct.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$[0].productId").value(testProduct.getId()));
        }
}
