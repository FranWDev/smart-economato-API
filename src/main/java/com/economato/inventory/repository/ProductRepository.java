package com.economato.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.dto.response.ProductResponseDTO;
import com.economato.inventory.model.Product;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    boolean existsByName(String name);

    List<Product> findByType(String type);

    List<Product> findByNameContainingIgnoreCase(String namePart);

    Page<Product> findByNameContainingIgnoreCase(String namePart, Pageable pageable);

    List<Product> findByCurrentStockLessThan(BigDecimal stock);

    List<Product> findByUnitPriceBetween(BigDecimal min, BigDecimal max);

    Optional<Product> findByProductCode(String productCode);

    Optional<Product> findById(ProductResponseDTO product2);

    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdOptimized(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findByIdsForUpdate(@Param("ids") List<Integer> ids);


    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForRead(@Param("id") Integer id);

    boolean existsBySupplierId(Integer supplierId);

}
