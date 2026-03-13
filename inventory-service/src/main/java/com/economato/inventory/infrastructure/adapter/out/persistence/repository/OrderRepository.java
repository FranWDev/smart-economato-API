package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.projection.OrderProjection;
import com.economato.inventory.application.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.application.dto.response.OrderResponseDTO;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.domain.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {

       List<Order> findByUser(User user);

       List<Order> findByStatus(OrderStatus status);

       List<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);

       Optional<OrderDetailResponseDTO> findById(OrderResponseDTO order2);

       @Query("SELECT DISTINCT o FROM Order o " +
                     "LEFT JOIN FETCH o.details d " +
                     "LEFT JOIN FETCH d.product " +
                     "LEFT JOIN FETCH o.user " +
                     "WHERE o.id = :id")
       Optional<Order> findByIdWithDetails(@Param("id") Integer id);

       /**
        * Busca una orden por ID con bloqueo pesimista para actualizaciones
        * concurrentes
        * Utiliza PESSIMISTIC_WRITE para prevenir conflictos de escritura
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT o FROM Order o WHERE o.id = :id")
       Optional<Order> findByIdForUpdate(@Param("id") Integer id);

       @Query("SELECT DISTINCT o FROM Order o " +
                     "LEFT JOIN FETCH o.details d " +
                     "LEFT JOIN FETCH d.product " +
                     "LEFT JOIN FETCH o.user")
       List<Order> findAllWithDetails();

       @Query("SELECT DISTINCT o FROM Order o " +
                     "LEFT JOIN FETCH o.details d " +
                     "LEFT JOIN FETCH d.product " +
                     "LEFT JOIN FETCH o.user " +
                     "WHERE o.status = :status")
       List<Order> findByStatusWithDetails(@Param("status") OrderStatus status);

       @Query("SELECT DISTINCT o FROM Order o " +
                     "LEFT JOIN FETCH o.details d " +
                     "LEFT JOIN FETCH d.product " +
                     "LEFT JOIN FETCH o.user u " +
                     "WHERE u.id = :userId")
       List<Order> findByUserIdWithDetails(@Param("userId") Integer userId);

       @Query("SELECT DISTINCT o FROM Order o " +
                     "LEFT JOIN FETCH o.details d " +
                     "LEFT JOIN FETCH d.product " +
                     "LEFT JOIN FETCH o.user " +
                     "WHERE o.orderDate BETWEEN :start AND :end")
       List<Order> findByOrderDateBetweenWithDetails(
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       @Query("SELECT o FROM Order o WHERE o.status = :status")
       Page<Order> findByStatusWithDetailsPageable(@Param("status") OrderStatus status, Pageable pageable);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       Page<Order> findAll(Pageable pageable);

       // --- Proyecciones ---

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       Page<OrderProjection> findAllProjectedBy(Pageable pageable);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       Optional<OrderProjection> findProjectedById(Integer id);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       Page<OrderProjection> findProjectedByStatus(OrderStatus status, Pageable pageable);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       List<OrderProjection> findProjectedByUserId(Integer userId);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       List<OrderProjection> findProjectedByOrderDateBetween(LocalDateTime start, LocalDateTime end);

       @EntityGraph(attributePaths = { "details", "details.product", "user", "supplier" })
       List<Order> findAll(Specification<Order> spec);

       @Query("SELECT COALESCE(SUM(od.quantity * p.unitPrice), 0) " +
                     "FROM Order o " +
                     "JOIN o.details od " +
                     "JOIN od.product p")
       java.math.BigDecimal getTotalCostAllOrders();

       @Query("SELECT DISTINCT o FROM Order o " +
                      "JOIN o.details od " +
                      "WHERE o.supplier.id = :supplierId " +
                      "AND od.product.id IN :productIds " +
                      "AND o.status = com.economato.inventory.domain.model.OrderStatus.CONFIRMED " +
                      "AND o.orderDate BETWEEN :startDate AND :endDate")
       List<Order> findConfirmedOrdersBySupplierAndProductIdsAndDateRange(
                      @Param("supplierId") Integer supplierId,
                      @Param("productIds") List<Integer> productIds,
                      @Param("startDate") LocalDateTime startDate,
                      @Param("endDate") LocalDateTime endDate);
}