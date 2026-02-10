package com.economato.inventory.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.economato.inventory.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.dto.request.OrderRequestDTO;
import com.economato.inventory.dto.response.OrderResponseDTO;
import com.economato.inventory.service.OrderPdfService;
import com.economato.inventory.service.OrderService;
import com.economato.inventory.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Ordenes", description = "Gestión de pedidos, incluyendo creación, actualización, búsqueda y filtrado por usuario, estado o rango de fechas.")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final OrderPdfService orderPdfService;

    public OrderController(OrderService orderService, UserService userService, OrderPdfService orderPdfService) {
        this.orderService = orderService;
        this.userService = userService;
        this.orderPdfService = orderPdfService;
    }

    @Operation(
        summary = "Obtener todos los pedidos",
        description = "Devuelve una lista paginada de todos los pedidos registrados en el sistema. [Rol requerido: CHEF]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de pedidos obtenida correctamente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponseDTO.class)))
        }
    )
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> getAll(
            @Parameter(description = "Información de paginación") Pageable pageable) {
        return ResponseEntity.ok(orderService.findAll(pageable));
    }

    @Operation(
        summary = "Obtener un pedido por ID",
        description = "Busca un pedido específico a partir de su identificador único. [Rol requerido: USER]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Pedido encontrado correctamente"),
            @ApiResponse(responseCode = "404", description = "No se encontró un pedido con el ID especificado")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getById(
            @Parameter(description = "ID del pedido a buscar", example = "10") @PathVariable Integer id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Descargar pedido en PDF",
        description = "Genera y descarga un PDF con los detalles del pedido (usuario, fecha, estado, productos y total). [Rol requerido: USER]",
        responses = {
            @ApiResponse(responseCode = "200", description = "PDF generado correctamente",
                    content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "404", description = "Pedido no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error al generar el PDF")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadOrderPdf(
            @Parameter(description = "ID del pedido", example = "10") @PathVariable Integer id) {
        return orderService.findById(id)
                .<ResponseEntity<byte[]>>map(order -> {
                    try {
                        byte[] pdfBytes = orderPdfService.generateOrderPdf(order);

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_PDF);
                        headers.setContentDisposition(ContentDisposition.attachment()
                                .filename("pedido_" + order.getId() + ".pdf")
                                .build());
                        headers.setContentLength(pdfBytes.length);

                        return ResponseEntity.ok()
                                .headers(headers)
                                .body(pdfBytes);
                    } catch (Exception e) {
                        return ResponseEntity.<byte[]>internalServerError().build();
                    }
                })
                .orElse(ResponseEntity.<byte[]>notFound().build());
    }

    @Operation(
        summary = "Crear un nuevo pedido",
        description = "Registra un pedido nuevo en el sistema, asociado a un usuario y con una lista de productos. [Rol requerido: USER]",
        requestBody = @RequestBody(
            description = "Datos del pedido a crear",
            required = true,
            content = @Content(schema = @Schema(implementation = OrderRequestDTO.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Pedido creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o campos requeridos faltantes")
        }
    )
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @PostMapping
    public ResponseEntity<OrderResponseDTO> create(@Valid @org.springframework.web.bind.annotation.RequestBody OrderRequestDTO orderRequest) {
        return ResponseEntity.ok(orderService.save(orderRequest));
    }

    @Operation(
        summary = "Actualizar un pedido existente",
        description = "Permite modificar los detalles de un pedido ya existente, incluyendo sus productos asociados. " +
                      "Este endpoint utiliza **bloqueo optimista** con reintentos automáticos (@Retryable) para manejar " +
                      "actualizaciones concurrentes. Se realizan hasta 3 intentos con backoff exponencial (100ms inicial). " +
                      "Si después de los reintentos persiste el conflicto, se retorna error 409. [Rol requerido: CHEF]",
        requestBody = @RequestBody(
            description = "Datos del pedido actualizados",
            required = true,
            content = @Content(schema = @Schema(implementation = OrderRequestDTO.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Pedido actualizado correctamente"),
            @ApiResponse(responseCode = "404", description = "No se encontró el pedido a actualizar"),
            @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia persistente después de 3 reintentos")
        }
    )
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> update(
            @Parameter(description = "ID del pedido a actualizar", example = "5") @PathVariable Integer id,
            @Valid @org.springframework.web.bind.annotation.RequestBody OrderRequestDTO orderRequest) {
        return orderService.update(id, orderRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Eliminar un pedido",
        description = "Elimina permanentemente un pedido del sistema si existe. [Rol requerido: ADMIN]",
        responses = {
            @ApiResponse(responseCode = "204", description = "Pedido eliminado correctamente"),
            @ApiResponse(responseCode = "404", description = "No se encontró el pedido a eliminar")
        }
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(
            @Parameter(description = "ID del pedido a eliminar", example = "12") @PathVariable Integer id) {
        return orderService.findById(id)
                .map(existing -> {
                    orderService.deleteById(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Obtener pedidos por usuario",
        description = "Devuelve todos los pedidos realizados por un usuario específico. [Rol requerido: USER]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de pedidos del usuario obtenida correctamente"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
        }
    )
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN') or #userId == authentication.principal.id")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponseDTO>> getByUser(
            @Parameter(description = "ID del usuario asociado a los pedidos", example = "3") @PathVariable Integer userId) {
        return userService.findById(userId)
                .map(user -> ResponseEntity.ok(orderService.findByUser(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Obtener pedidos por estado",
        description = "Permite listar todos los pedidos que se encuentran en un estado determinado, como 'PENDIENTE', 'ENVIADO' o 'CANCELADO'. [Rol requerido: CHEF]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de pedidos filtrada correctamente")
        }
    )
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping("/status/{status}")
    public List<OrderResponseDTO> getByStatus(
            @Parameter(description = "Estado del pedido", example = "PENDIENTE") @PathVariable String status) {
        return orderService.findByStatus(status);
    }

    @Operation(
        summary = "Obtener pedidos por rango de fechas",
        description = "Filtra los pedidos que fueron creados entre dos fechas específicas (formato ISO-8601). [Rol requerido: CHEF]",
        parameters = {
            @Parameter(name = "start", description = "Fecha de inicio (formato: 2025-01-01T00:00:00)", required = true),
            @Parameter(name = "end", description = "Fecha de fin (formato: 2025-01-31T23:59:59)", required = true)
        },
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de pedidos en el rango especificado")
        }
    )
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping("/daterange")
    public List<OrderResponseDTO> getByDateRange(
            @RequestParam String start,
            @RequestParam String end) {
        LocalDateTime startDate = LocalDateTime.parse(start);
        LocalDateTime endDate = LocalDateTime.parse(end);
        return orderService.findByDateRange(startDate, endDate);
    }

    @Operation(
        summary = "Obtener órdenes pendientes de recepción",
        description = "Devuelve todas las órdenes que están en estado PENDING y necesitan ser recibidas. [Rol requerido: CHEF]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de órdenes pendientes obtenida correctamente")
        }
    )
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping("/reception/pending")
    public ResponseEntity<List<OrderResponseDTO>> getPendingReception() {
        return ResponseEntity.ok(orderService.findPendingReception());
    }

    @Operation(
        summary = "Procesar recepción de una orden",
        description = "Procesa la recepción de una orden, validando cantidades y actualizando el inventario si se confirma. " +
                      "Este endpoint utiliza **bloqueo pesimista (Pessimistic Locking)** con nivel de aislamiento " +
                      "REPEATABLE_READ para garantizar la consistencia del stock durante actualizaciones concurrentes. " +
                      "El bloqueo se aplica a los productos involucrados para prevenir condiciones de carrera " +
                      "mientras se actualiza el inventario. [Rol requerido: CHEF]",
        requestBody = @RequestBody(
            description = "Datos de la recepción (orden, productos y estado)",
            required = true,
            content = @Content(schema = @Schema(implementation = OrderReceptionRequestDTO.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Recepción procesada correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, validación de cantidades fallida, o cantidades recibidas menores a las solicitadas"),
            @ApiResponse(responseCode = "404", description = "Orden o producto no encontrado")
        }
    )
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PostMapping("/reception")
    public ResponseEntity<OrderResponseDTO> receiveOrder(
            @Valid @org.springframework.web.bind.annotation.RequestBody OrderReceptionRequestDTO receptionData) {
        try {
            OrderResponseDTO result = orderService.receiveOrder(receptionData);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
        summary = "Actualizar estado de una orden",
        description = "Permite cambiar el estado de una orden entre: CREATED, PENDING, REVIEW, COMPLETED, INCOMPLETE. [Rol requerido: CHEF]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Estado actualizado correctamente"),
            @ApiResponse(responseCode = "404", description = "No se encontró la orden"),
            @ApiResponse(responseCode = "400", description = "Estado inválido")
        }
    )
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponseDTO> updateOrderStatus(
            @Parameter(description = "ID de la orden", example = "5") @PathVariable Integer id,
            @org.springframework.web.bind.annotation.RequestBody UpdateStatusRequest statusRequest) {
        try {
            return orderService.updateStatus(id, statusRequest.getStatus())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO para actualizar estado de orden
     */
    public static class UpdateStatusRequest {
        private String status;

        public UpdateStatusRequest() {}

        public UpdateStatusRequest(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
