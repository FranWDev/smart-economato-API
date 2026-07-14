package com.economato.inventory.infrastructure.adapter.out.persistence.repository.crisis;

import com.economato.inventory.domain.model.crisis.FoodCrisis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodCrisisRepository extends JpaRepository<FoodCrisis, Long> {
    Optional<FoodCrisis> findByCrisisCode(String crisisCode);

       List<FoodCrisis> findByStatus(FoodCrisis.CrisisStatus status);

       boolean existsByStatusAndSupplierId(FoodCrisis.CrisisStatus status, Integer supplierId);

    @Query("SELECT DISTINCT f FROM FoodCrisis f " +
           "LEFT JOIN FETCH f.supplier " +
           "LEFT JOIN FETCH f.affectedProducts ap " +
           "LEFT JOIN FETCH ap.product p " +
           "LEFT JOIN FETCH f.activatedBy " +
           "LEFT JOIN FETCH f.liftedBy " +
           "WHERE f.id = :id")
    Optional<FoodCrisis> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT f FROM FoodCrisis f LEFT JOIN FETCH f.supplier")
    List<FoodCrisis> findAllWithSupplier();

    @Query("SELECT DISTINCT f FROM FoodCrisis f " +
           "LEFT JOIN FETCH f.supplier " +
           "LEFT JOIN FETCH f.affectedProducts ap " +
           "LEFT JOIN FETCH ap.product " +
           "LEFT JOIN FETCH f.activatedBy " +
           "LEFT JOIN FETCH f.liftedBy " +
           "WHERE f.status = :status")
    List<FoodCrisis> findByStatusWithDetails(@Param("status") FoodCrisis.CrisisStatus status);

    @Query(value = "SELECT f FROM FoodCrisis f LEFT JOIN FETCH f.supplier WHERE f.status = 'LIFTED' AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR " +
           "LOWER(f.crisisCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(f.supplier.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(f.reason) LIKE LOWER(CONCAT('%', :searchTerm, '%')))",
           countQuery = "SELECT COUNT(f) FROM FoodCrisis f WHERE f.status = 'LIFTED' AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR " +
           "LOWER(f.crisisCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(f.supplier.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(f.reason) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<FoodCrisis> findHistoryWithSearch(@Param("searchTerm") String searchTerm, Pageable pageable);
}
