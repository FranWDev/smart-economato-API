package com.economato.inventory.application.usecase.mcp;

import com.economato.inventory.application.dto.mcp.*;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class McpUtilityService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final RecipeRepository recipeRepository;
    private final SupplierRepository supplierRepository;

    public McpUtilityService(ProductRepository productRepository, 
                             OrderRepository orderRepository,
                             RecipeRepository recipeRepository, 
                             SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.recipeRepository = recipeRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public McpSystemContextDto getSystemContext() {
        return McpSystemContextDto.builder()
                .totalProducts(productRepository.count())
                .pendingOrdersCount(getPendingOrders().size())
                .totalRecipes(recipeRepository.count())
                .activeAlertsCount(0) 
                .build();
    }

    @Transactional(readOnly = true)
    public List<McpProductDto> getProductsWithFilters(BigDecimal minPrice, BigDecimal maxPrice, Boolean lowStock) {
        return productRepository.findAll().stream()
                .filter(p -> (minPrice == null || p.getUnitPrice().compareTo(minPrice) >= 0))
                .filter(p -> (maxPrice == null || p.getUnitPrice().compareTo(maxPrice) <= 0))
                .map(this::mapToProductDto)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public List<McpOrderDto> getPendingOrders() {
        return orderRepository.findByStatusInWithDetails(Arrays.asList(
                OrderStatus.PENDING, 
                OrderStatus.REVIEW, 
                OrderStatus.CREATED)).stream()
                .map(this::mapToOrderDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<McpProductDto> getProductsBulk(McpBulkRequest request) {
        List<Product> products;
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            products = productRepository.findAllById(request.getIds());
        } else if (request.getCodes() != null && !request.getCodes().isEmpty()) {
            products = productRepository.findAll().stream()
                    .filter(p -> request.getCodes().contains(p.getProductCode()))
                    .collect(Collectors.toList());
        } else {
            return List.of();
        }
        return products.stream().map(this::mapToProductDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<McpOrderDto> getOrdersBulk(List<Integer> ids) {
        return orderRepository.findAllByIdWithDetails(ids).stream()
                .map(this::mapToOrderDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<McpRecipeDto> getRecipesBulk(List<Integer> ids) {
        return recipeRepository.findAllByIdWithAllergens(ids).stream()
                .map(this::mapToRecipeDto)
                .collect(Collectors.toList());
    }

    private McpProductDto mapToProductDto(Product p) {
        return McpProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .code(p.getProductCode())
                .stock(p.getCurrentStock())
                .unit(p.getUnit())
                .price(p.getUnitPrice())
                .build();
    }

    private McpOrderDto mapToOrderDto(Order o) {
        return McpOrderDto.builder()
                .id(o.getId())
                .status(o.getStatus().name())
                .totalAmount(o.getDetails().stream()
                        .map(d -> d.getProduct().getUnitPrice().multiply(d.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .itemCount(o.getDetails().size())
                .supplierName(o.getSupplier() != null ? o.getSupplier().getName() : "N/A")
                .orderDate(o.getOrderDate().toString())
                .build();
    }

    private McpRecipeDto mapToRecipeDto(Recipe r) {
        return McpRecipeDto.builder()
                .id(r.getId())
                .name(r.getName())
                .code(r.getId().toString())
                .cost(r.getTotalCost())
                .allergenCount(r.getAllergens().size())
                .description(r.getPresentation())
                .preparation(r.getElaboration())
                .build();
    }
}
