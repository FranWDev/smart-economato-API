package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.CrisisAffectedProduct;
import com.economato.inventory.domain.model.FoodCrisis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;
import com.economato.inventory.domain.model.Product;

public interface CrisisAffectedProductRepository extends JpaRepository<CrisisAffectedProduct, Long> {
    List<CrisisAffectedProduct> findByFoodCrisis(FoodCrisis foodCrisis);
    List<CrisisAffectedProduct> findByFoodCrisisId(Long foodCrisisId);
    List<CrisisAffectedProduct> findByProductInAndFoodCrisisStatus(Collection<Product> products, FoodCrisis.CrisisStatus status);
}
