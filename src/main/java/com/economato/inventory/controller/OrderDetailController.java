package com.economato.inventory.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.economato.inventory.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.service.OrderDetailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/order-details")
@Tag(name = "Detalles de Orden", description = "Operaciones relacionadas con los detalles de pedidos")
public class OrderDetailController {

    private final OrderDetailService orderDetailService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderDetailController(OrderDetailService orderDetailService, 
                                 OrderRepository orderRepository,
                                 ProductRepository productRepository) {
        this.orderDetailService = orderDetailService;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Operation(summary = "Obtener todos los detalles de pedido",
               description = "Devuelve una lista paginada de todos los detalles de pedidos existentes.")
    @ApiResponse(responseCode = "200", description = "Lista obtenida correctamente",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = OrderDetailResponseDTO.class)))
    @GetMapping
    public ResponseEntity<List<OrderDetailResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(orderDetailService.findAll(pageable));
    }

    @Operation(summary = "Obtener un detalle de pedido específico",
               description = "Devuelve el detalle de un pedido según el ID del pedido y del producto.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle encontrado",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = OrderDetailResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "No se encontró el detalle")
    })
    @GetMapping("/{orderId}/{productId}")
    public ResponseEntity<OrderDetailResponseDTO> getById(
            @Parameter(description = "ID del pedido", example = "10") @PathVariable Integer orderId,
            @Parameter(description = "ID del producto", example = "42") @PathVariable Integer productId) {
        return orderDetailService.findById(orderId, productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Crear un nuevo detalle de pedido",
               description = "Agrega un nuevo detalle a un pedido existente.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Detalle creado correctamente",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = OrderDetailResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    @PostMapping
    public ResponseEntity<OrderDetailResponseDTO> create(
            @Valid @org.springframework.web.bind.annotation.RequestBody OrderDetailRequestDTO orderDetailRequest) {
        OrderDetailResponseDTO response = orderDetailService.save(orderDetailRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Actualizar un detalle de pedido",
               description = "Modifica un detalle de pedido existente según el pedido y producto.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle actualizado correctamente",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = OrderDetailResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Detalle no encontrado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    @PutMapping("/{orderId}/{productId}")
    public ResponseEntity<OrderDetailResponseDTO> update(
            @Parameter(description = "ID del pedido", example = "10") @PathVariable Integer orderId,
            @Parameter(description = "ID del producto", example = "42") @PathVariable Integer productId,
            @Valid @org.springframework.web.bind.annotation.RequestBody OrderDetailRequestDTO orderDetailRequest) {
        return orderDetailService.update(orderId, productId, orderDetailRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Eliminar un detalle de pedido",
               description = "Elimina un detalle específico dentro de un pedido.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Detalle eliminado correctamente"),
        @ApiResponse(responseCode = "404", description = "Detalle no encontrado")
    })
    @DeleteMapping("/{orderId}/{productId}")
    public ResponseEntity<Object> delete(
            @Parameter(description = "ID del pedido", example = "10") @PathVariable Integer orderId,
            @Parameter(description = "ID del producto", example = "42") @PathVariable Integer productId) {
        return orderDetailService.findById(orderId, productId)
                .map(existing -> {
                    orderDetailService.deleteById(orderId, productId);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Obtener detalles por pedido",
               description = "Devuelve todos los detalles asociados a un pedido concreto.")
    @ApiResponse(responseCode = "200", description = "Detalles obtenidos correctamente")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderDetailResponseDTO>> getByOrder(
            @Parameter(description = "ID del pedido", example = "10") @PathVariable Integer orderId) {
        return orderRepository.findById(orderId)
                .map(order -> ResponseEntity.ok(orderDetailService.findByOrder(order)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Obtener detalles por producto",
               description = "Devuelve todos los detalles de pedidos donde aparece un producto específico.")
    @ApiResponse(responseCode = "200", description = "Detalles obtenidos correctamente")
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<OrderDetailResponseDTO>> getByProduct(
            @Parameter(description = "ID del producto", example = "42") @PathVariable Integer productId) {
        return productRepository.findById(productId)
                .map(product -> ResponseEntity.ok(orderDetailService.findByProduct(product)))
                .orElse(ResponseEntity.notFound().build());
    }
}