package com.economato.inventory.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.OrderDetailMapper;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.OrderDetail;
import com.economato.inventory.model.OrderDetailId;
import com.economato.inventory.model.Product;
import com.economato.inventory.repository.OrderDetailRepository;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.ProductRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
public class OrderDetailService {

    private final OrderDetailRepository repository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderDetailMapper orderDetailMapper;

    public OrderDetailService(OrderDetailRepository repository,
                              OrderRepository orderRepository,
                              ProductRepository productRepository,
                              OrderDetailMapper orderDetailMapper) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderDetailMapper = orderDetailMapper;
    }

    @Transactional(readOnly = true)
    public List<OrderDetailResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).stream()
                .map(orderDetailMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<OrderDetailResponseDTO> findById(Integer orderId, Integer productId) {
        return repository.findById(new OrderDetailId(orderId, productId))
                .map(orderDetailMapper::toResponseDTO);
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
    public OrderDetailResponseDTO save(OrderDetailRequestDTO requestDTO) {
        OrderDetail orderDetail = toEntity(requestDTO);
        return orderDetailMapper.toResponseDTO(repository.save(orderDetail));
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
    public Optional<OrderDetailResponseDTO> update(Integer orderId, Integer productId,
                                                   OrderDetailRequestDTO requestDTO) {
        return repository.findById(new OrderDetailId(orderId, productId))
                .map(existing -> {
                    updateEntity(existing, requestDTO);
                    return orderDetailMapper.toResponseDTO(repository.save(existing));
                });
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
    public void deleteById(Integer orderId, Integer productId) {
        repository.deleteById(new OrderDetailId(orderId, productId));
    }

    // CORREGIDO: Ahora recibe Order (entidad) en lugar de OrderResponseDTO
    @Transactional(readOnly = true)
    public List<OrderDetailResponseDTO> findByOrder(Order order) {
        return repository.findByOrderId(order.getId()).stream()
                .map(orderDetailMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // CORREGIDO: Ahora recibe Product (entidad) en lugar de ProductResponseDTO
    @Transactional(readOnly = true)
    public List<OrderDetailResponseDTO> findByProduct(Product product) {
        return repository.findByProductId(product.getId()).stream()
                .map(orderDetailMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    private OrderDetail toEntity(OrderDetailRequestDTO requestDTO) {
        Order order = orderRepository.findById(requestDTO.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        Product product = productRepository.findById(requestDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrder(order);
        orderDetail.setProduct(product);
        orderDetail.setQuantity(requestDTO.getQuantity());
        orderDetail.setId(new OrderDetailId(order.getId(), product.getId()));

        return orderDetail;
    }

    private void updateEntity(OrderDetail orderDetail, OrderDetailRequestDTO requestDTO) {
        // Solo actualizamos la cantidad, no el producto
        // Si necesitas cambiar el producto, sería mejor eliminar y crear uno nuevo
        orderDetail.setQuantity(requestDTO.getQuantity());
    }
}