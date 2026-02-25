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
                                .map(orderDetailMapper::toResponseDTO)
                                .toList();
        }

        @Transactional(readOnly = true)
        public Optional<OrderDetailResponseDTO> findById(Integer orderId, Integer productId) {
                return repository.findProjectedById(orderId, productId)
                                .map(orderDetailMapper::toResponseDTO);
        }

        @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
                        RuntimeException.class, Exception.class })
        public OrderDetailResponseDTO save(OrderDetailRequestDTO requestDTO) {
                Order order = orderRepository.findById(requestDTO.getOrderId())
                                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

                Product product = productRepository.findById(requestDTO.getProductId())
                                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

                OrderDetail orderDetail = orderDetailMapper.toEntity(requestDTO);
                orderDetail.setOrder(order);
                orderDetail.setProduct(product);
                orderDetail.setId(new OrderDetailId(order.getId(), product.getId()));

                repository.save(orderDetail);

                return repository.findProjectedById(orderDetail.getOrder().getId(), orderDetail.getProduct().getId())
                                .map(orderDetailMapper::toResponseDTO)
                                .orElseThrow(() -> new RuntimeException("Saved detail not found"));
        }

        @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
                        RuntimeException.class, Exception.class })
        public Optional<OrderDetailResponseDTO> update(Integer orderId, Integer productId,
                        OrderDetailRequestDTO requestDTO) {
                return repository.findById(new OrderDetailId(orderId, productId))
                                .map(existing -> {
                                        orderDetailMapper.updateEntityFromDto(requestDTO, existing);
                                        repository.save(existing);

                                        return repository.findProjectedById(orderId, productId)
                                                        .map(orderDetailMapper::toResponseDTO)
                                                        .orElseThrow(() -> new RuntimeException(
                                                                        "Updated detail not found"));
                                });
        }

        @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
                        RuntimeException.class, Exception.class })
        public void deleteById(Integer orderId, Integer productId) {
                repository.deleteById(new OrderDetailId(orderId, productId));
        }

        @Transactional(readOnly = true)
        public List<OrderDetailResponseDTO> findByOrder(Order order) {
                return findByOrderId(order.getId());
        }

        @Transactional(readOnly = true)
        public List<OrderDetailResponseDTO> findByOrderId(Integer orderId) {
                return repository.findProjectedByOrderId(orderId).stream()
                                .map(orderDetailMapper::toResponseDTO)
                                .toList();
        }

        @Transactional(readOnly = true)
        public List<OrderDetailResponseDTO> findByProduct(Product product) {
                return findByProductId(product.getId());
        }

        @Transactional(readOnly = true)
        public List<OrderDetailResponseDTO> findByProductId(Integer productId) {
                return repository.findProjectedByProductId(productId).stream()
                                .map(orderDetailMapper::toResponseDTO)
                                .toList();
        }

}