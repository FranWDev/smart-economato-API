package com.economato.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.model.InventoryAudit;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryAuditRepository extends JpaRepository<InventoryAudit, Integer> {

       boolean existsByProductId(Integer productId);

       List<InventoryAudit> findByProduct(Product product);

       List<InventoryAudit> findByUser(User user);

       List<InventoryAudit> findByMovementType(String movementType);

       List<InventoryAudit> findByMovementDateBetween(LocalDateTime start, LocalDateTime end);

       @Query("SELECT ia FROM InventoryAudit ia " +
                     "LEFT JOIN FETCH ia.product " +
                     "LEFT JOIN FETCH ia.user " +
                     "WHERE ia.movementType = :movementType")
       List<InventoryAudit> findByMovementTypeWithDetails(@Param("movementType") String movementType);

       @Query("SELECT ia FROM InventoryAudit ia " +
                     "LEFT JOIN FETCH ia.product " +
                     "LEFT JOIN FETCH ia.user " +
                     "WHERE ia.movementDate BETWEEN :start AND :end")
       List<InventoryAudit> findByMovementDateBetweenWithDetails(
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

       // --- Proyecciones ---

       @Query("SELECT ia FROM InventoryAudit ia")
       org.springframework.data.domain.Page<com.economato.inventory.dto.projection.InventoryAuditProjection> findAllProjectedBy(
                     org.springframework.data.domain.Pageable pageable);

       @Query("SELECT ia FROM InventoryAudit ia WHERE ia.id = :id")
       java.util.Optional<com.economato.inventory.dto.projection.InventoryAuditProjection> findProjectedById(
                     @Param("id") Integer id);

       @Query("SELECT ia FROM InventoryAudit ia WHERE ia.movementType = :movementType")
       List<com.economato.inventory.dto.projection.InventoryAuditProjection> findProjectedByMovementType(
                     @Param("movementType") String movementType);

       @Query("SELECT ia FROM InventoryAudit ia WHERE ia.movementDate BETWEEN :start AND :end")
       List<com.economato.inventory.dto.projection.InventoryAuditProjection> findProjectedByMovementDateBetween(
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);
}
