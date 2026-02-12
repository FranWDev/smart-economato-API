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
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
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
        return repository.findAllProjectedBy(pageable).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<OrderDetailResponseDTO> findById(Integer orderId, Integer productId) {
        return repository.findProjectedById(orderId, productId)
                .map(this::toResponseDTO);
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public OrderDetailResponseDTO save(OrderDetailRequestDTO requestDTO) {
        OrderDetail orderDetail = toEntity(requestDTO);
        repository.save(orderDetail);

        return repository.findProjectedById(orderDetail.getOrder().getId(), orderDetail.getProduct().getId())
                .map(this::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Saved detail not found"));
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<OrderDetailResponseDTO> update(Integer orderId, Integer productId,
            OrderDetailRequestDTO requestDTO) {
        return repository.findById(new OrderDetailId(orderId, productId))
                .map(existing -> {
                    updateEntity(existing, requestDTO);
                    repository.save(existing);

                    return repository.findProjectedById(orderId, productId)
                            .map(this::toResponseDTO)
                            .orElseThrow(() -> new RuntimeException("Updated detail not found"));
                });
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer orderId, Integer productId) {
        repository.deleteById(new OrderDetailId(orderId, productId));
    }

    // CORREGIDO: Ahora recibe Order (entidad) en lugar de OrderResponseDTO
    @Transactional(readOnly = true)
    public List<OrderDetailResponseDTO> findByOrder(Order order) {
        return repository.findProjectedByOrderId(order.getId()).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    // CORREGIDO: Ahora recibe Product (entidad) en lugar de ProductResponseDTO
    @Transactional(readOnly = true)
    public List<OrderDetailResponseDTO> findByProduct(Product product) {
        return repository.findProjectedByProductId(product.getId()).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convierte una proyección de OrderDetail a OrderDetailResponseDTO.
     */
    private OrderDetailResponseDTO toResponseDTO(
            com.economato.inventory.dto.projection.OrderDetailProjection projection) {
        OrderDetailResponseDTO dto = new OrderDetailResponseDTO();
        if (projection.getOrder() != null) {
            dto.setOrderId(projection.getOrder().getId());
        }

        dto.setQuantity(projection.getQuantity());
        dto.setQuantityReceived(projection.getQuantityReceived());

        if (projection.getProduct() != null) {
            dto.setProductId(projection.getProduct().getId());
            dto.setProductName(projection.getProduct().getName());
            dto.setUnitPrice(projection.getProduct().getUnitPrice());

            // subtotal
            if (projection.getQuantity() != null && dto.getUnitPrice() != null) {
                dto.setSubtotal(projection.getQuantity().multiply(dto.getUnitPrice()));
            }
        }

        return dto;
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