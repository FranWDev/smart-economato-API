package com.economato.inventory.infrastructure.adapter.out.persistence.repository.crisis;

import com.economato.inventory.domain.model.crisis.CrisisAffectedProduct;
import com.economato.inventory.domain.model.crisis.FoodCrisis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import com.economato.inventory.domain.model.product.Product;

public interface CrisisAffectedProductRepository extends JpaRepository<CrisisAffectedProduct, Long> {
    List<CrisisAffectedProduct> findByFoodCrisis(FoodCrisis foodCrisis);
    List<CrisisAffectedProduct> findByFoodCrisisId(Long foodCrisisId);
    List<CrisisAffectedProduct> findByProductInAndFoodCrisisStatus(Collection<Product> products, FoodCrisis.CrisisStatus status);

    @Query("SELECT cap FROM CrisisAffectedProduct cap JOIN FETCH cap.product WHERE cap.foodCrisis.id IN :crisisIds")
    List<CrisisAffectedProduct> findByFoodCrisisIdIn(@Param("crisisIds") Collection<Long> crisisIds);

    @Query("SELECT cap FROM CrisisAffectedProduct cap JOIN FETCH cap.product WHERE cap.foodCrisis.id = :crisisId")
    List<CrisisAffectedProduct> findByFoodCrisisIdWithProduct(@Param("crisisId") Long crisisId);
}
