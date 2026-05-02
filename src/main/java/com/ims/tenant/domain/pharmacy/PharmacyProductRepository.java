package com.ims.tenant.domain.pharmacy;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.ims.product.ProductExpiryView;

@Repository
public interface PharmacyProductRepository extends JpaRepository<PharmacyProduct, Long> {
  List<PharmacyProduct> findByExpiryDateBefore(LocalDate date);

  @Query("SELECT p.id as id, p.name as name, p.sku as sku, "
      + "pp.batchNumber as batchNumber, pp.expiryDate as expiryDate, pp.manufacturer as manufacturer "
      + "FROM PharmacyProduct pp JOIN pp.product p "
      + "WHERE pp.expiryDate <= :date AND p.active = true")
  List<ProductExpiryView> findExpiring(@Param("date") LocalDate date);

  @Query("SELECT COUNT(pp) FROM PharmacyProduct pp JOIN pp.product p "
      + "WHERE pp.expiryDate <= :date AND p.active = true")
  long countExpiring(@Param("date") LocalDate date);
}
