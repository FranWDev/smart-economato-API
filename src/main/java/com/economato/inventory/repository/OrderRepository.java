package com.economato.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.dto.response.OrderResponseDTO;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {

       List<Order> findByUser(User user);

       List<Order> findByStatus(String status);

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
       List<Order> findByStatusWithDetails(@Param("status") String status);

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
       Page<Order> findByStatusWithDetailsPageable(@Param("status") String status, Pageable pageable);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       Page<Order> findAll(Pageable pageable);

       // --- Proyecciones ---

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       Page<com.economato.inventory.dto.projection.OrderProjection> findAllProjectedBy(Pageable pageable);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       Optional<com.economato.inventory.dto.projection.OrderProjection> findProjectedById(Integer id);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       Page<com.economato.inventory.dto.projection.OrderProjection> findProjectedByStatus(String status,
                     Pageable pageable);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       List<com.economato.inventory.dto.projection.OrderProjection> findProjectedByUserId(Integer userId);

       @EntityGraph(attributePaths = { "details", "details.product", "user" })
       List<com.economato.inventory.dto.projection.OrderProjection> findProjectedByOrderDateBetween(LocalDateTime start,
                     LocalDateTime end);
}