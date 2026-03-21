package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.projection.InventoryAuditProjection;
import com.economato.inventory.domain.model.InventoryAudit;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryAuditRepository extends JpaRepository<InventoryAudit, Integer>, JpaSpecificationExecutor<InventoryAudit> {

       @Override
       @EntityGraph(attributePaths = {"product", "user"})
       Page<InventoryAudit> findAll(Specification<InventoryAudit> spec, Pageable pageable);

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

       @Query("SELECT ia FROM InventoryAudit ia")
       Page<InventoryAuditProjection> findAllProjectedBy(Pageable pageable);

       @Query("SELECT ia FROM InventoryAudit ia WHERE ia.id = :id")
       Optional<InventoryAuditProjection> findProjectedById(@Param("id") Integer id);

       @Query("SELECT ia FROM InventoryAudit ia WHERE ia.movementType = :movementType")
       List<InventoryAuditProjection> findProjectedByMovementType(
                     @Param("movementType") String movementType);

       @Query("SELECT ia FROM InventoryAudit ia WHERE ia.movementDate BETWEEN :start AND :end")
       List<InventoryAuditProjection> findProjectedByMovementDateBetween(
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

}
