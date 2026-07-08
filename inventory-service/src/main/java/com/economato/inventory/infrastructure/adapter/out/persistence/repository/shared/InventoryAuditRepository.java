package com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.shared.projection.InventoryAuditProjection;
import com.economato.inventory.domain.model.shared.InventoryAudit;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.user.User;

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

    Page<InventoryAuditProjection> findAllProjectedBy(Pageable pageable);

    Optional<InventoryAuditProjection> findProjectedById(Integer id);

    List<InventoryAuditProjection> findProjectedByMovementType(String movementType);

    List<InventoryAuditProjection> findProjectedByMovementDateBetween(LocalDateTime start, LocalDateTime end);

}
