package com.economatom.inventory.service;

import com.economatom.inventory.dto.response.OrderAuditResponseDTO;
import com.economatom.inventory.exception.ResourceNotFoundException;
import com.economatom.inventory.mapper.OrderAuditMapper;
import com.economatom.inventory.model.Order;
import com.economatom.inventory.model.OrderAudit;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.OrderAuditRepository;
import com.economatom.inventory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAuditServiceTest {

    @Mock
    private OrderAuditRepository orderAuditRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderAuditMapper orderAuditMapper;

    @InjectMocks
    private OrderAuditService orderAuditService;

    private Order testOrder;
    private User testUser;
    private OrderAudit testOrderAudit;
    private OrderAuditResponseDTO testOrderAuditResponseDTO;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setName("Test User");
        testUser.setEmail("test@test.com");

        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setStatus("PENDING");

        testOrderAudit = new OrderAudit();
        testOrderAudit.setId(1);
        testOrderAudit.setOrder(testOrder);
        testOrderAudit.setUsers(testUser);
        testOrderAudit.setAction("CAMBIO_ESTADO");
        testOrderAudit.setDetails("Estado cambiado de CREATED a PENDING");
        testOrderAudit.setPreviousState("{\"status\":\"CREATED\"}");
        testOrderAudit.setNewState("{\"status\":\"PENDING\"}");
        testOrderAudit.setAuditDate(LocalDateTime.now());

        testOrderAuditResponseDTO = new OrderAuditResponseDTO();
        testOrderAuditResponseDTO.setId(1);
        testOrderAuditResponseDTO.setOrderId(1);
        testOrderAuditResponseDTO.setUserId(1);
        testOrderAuditResponseDTO.setUserName("Test User");
        testOrderAuditResponseDTO.setAction("CAMBIO_ESTADO");
        testOrderAuditResponseDTO.setDetails("Estado cambiado de CREATED a PENDING");
        testOrderAuditResponseDTO.setPreviousState("{\"status\":\"CREATED\"}");
        testOrderAuditResponseDTO.setNewState("{\"status\":\"PENDING\"}");
        testOrderAuditResponseDTO.setAuditDate(LocalDateTime.now());
    }

    @Test
    void testLogOrderAction_Success() {

        when(orderAuditRepository.save(any(OrderAudit.class))).thenReturn(testOrderAudit);

        orderAuditService.logOrderAction(
                testOrder,
                "CAMBIO_ESTADO",
                "Estado cambiado de CREATED a PENDING",
                "{\"status\":\"CREATED\"}",
                "{\"status\":\"PENDING\"}");

        ArgumentCaptor<OrderAudit> auditCaptor = ArgumentCaptor.forClass(OrderAudit.class);
        verify(orderAuditRepository).save(auditCaptor.capture());

        OrderAudit capturedAudit = auditCaptor.getValue();
        assertEquals(testOrder, capturedAudit.getOrder());
        assertEquals("CAMBIO_ESTADO", capturedAudit.getAction());
        assertEquals("Estado cambiado de CREATED a PENDING", capturedAudit.getDetails());
        assertEquals("{\"status\":\"CREATED\"}", capturedAudit.getPreviousState());
        assertEquals("{\"status\":\"PENDING\"}", capturedAudit.getNewState());
    }

    @Test
    void testFindByOrderId_EmptyResult() {

        when(orderAuditRepository.findByOrderIdWithDetails(999)).thenReturn(Arrays.asList());

        List<OrderAuditResponseDTO> result = orderAuditService.findByOrderId(999);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(orderAuditRepository).findByOrderIdWithDetails(999);
    }

    @Test
    void testFindAll_Success() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<OrderAudit> auditPage = new PageImpl<>(Arrays.asList(testOrderAudit));

        when(orderAuditRepository.findAll(pageable)).thenReturn(auditPage);
        when(orderAuditMapper.toResponseDTO(any(OrderAudit.class))).thenReturn(testOrderAuditResponseDTO);

        List<OrderAuditResponseDTO> result = orderAuditService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testOrderAuditResponseDTO, result.get(0));
        verify(orderAuditRepository).findAll(pageable);
    }

    @Test
    void testFindById_Success() {

        when(orderAuditRepository.findById(1)).thenReturn(Optional.of(testOrderAudit));
        when(orderAuditMapper.toResponseDTO(testOrderAudit)).thenReturn(testOrderAuditResponseDTO);

        Optional<OrderAuditResponseDTO> result = orderAuditService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testOrderAuditResponseDTO, result.get());
        verify(orderAuditRepository).findById(1);
    }

    @Test
    void testFindById_NotFound() {

        when(orderAuditRepository.findById(999)).thenReturn(Optional.empty());

        Optional<OrderAuditResponseDTO> result = orderAuditService.findById(999);

        assertFalse(result.isPresent());
        verify(orderAuditRepository).findById(999);
    }

    @Test
    void testFindByOrderId_Success() {

        when(orderAuditRepository.findByOrderIdWithDetails(1)).thenReturn(Arrays.asList(testOrderAudit));
        when(orderAuditMapper.toResponseDTO(any(OrderAudit.class))).thenReturn(testOrderAuditResponseDTO);

        List<OrderAuditResponseDTO> result = orderAuditService.findByOrderId(1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testOrderAuditResponseDTO, result.get(0));
        verify(orderAuditRepository).findByOrderIdWithDetails(1);
    }

    @Test
    void testFindByUserId_Success() {

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(orderAuditRepository.findByUsersId(1)).thenReturn(Arrays.asList(testOrderAudit));
        when(orderAuditMapper.toResponseDTO(any(OrderAudit.class))).thenReturn(testOrderAuditResponseDTO);

        List<OrderAuditResponseDTO> result = orderAuditService.findByUserId(1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testOrderAuditResponseDTO, result.get(0));
        verify(orderAuditRepository).findByUsersId(1);
    }

    @Test
    void testFindByAuditDateBetween_Success() {

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        when(orderAuditRepository.findByAuditDateBetweenWithDetails(start, end))
                .thenReturn(Arrays.asList(testOrderAudit));
        when(orderAuditMapper.toResponseDTO(any(OrderAudit.class))).thenReturn(testOrderAuditResponseDTO);

        List<OrderAuditResponseDTO> result = orderAuditService.findByAuditDateBetween(start, end);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testOrderAuditResponseDTO, result.get(0));
        verify(orderAuditRepository).findByAuditDateBetweenWithDetails(start, end);
    }

    @Test
    void testFindByAuditDateBetween_EmptyResult() {

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        when(orderAuditRepository.findByAuditDateBetweenWithDetails(start, end)).thenReturn(Arrays.asList());

        List<OrderAuditResponseDTO> result = orderAuditService.findByAuditDateBetween(start, end);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(orderAuditRepository).findByAuditDateBetweenWithDetails(start, end);
    }
}
